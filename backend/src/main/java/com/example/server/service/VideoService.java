package com.example.server.service;

import com.example.server.dto.VideoDto;
import com.example.server.dto.VideoRequest;
import com.example.server.entity.UrbanResult;
import com.example.server.entity.User;
import com.example.server.entity.Video;
import com.example.server.exception.VideoNotFoundException;
import com.example.server.exception.VideoStorageException;
import com.example.server.property.FileStorageProperties;
import com.example.server.repository.UrbanResultRepository;
import com.example.server.repository.VideoRepository;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class VideoService {

    private final VideoRepository videoRepository;

    private final CustomUserDetailsService customUserDetailsService;
    private final UrbanResultRepository urbanResultRepository;

    private final UrbanResultService urbanResultService;

    private final Path videoStorageLocation;

    private final Path thumbnailStorageLocation;

    public VideoService(VideoRepository videoRepository, CustomUserDetailsService customUserDetailsService, UrbanResultRepository urbanResultRepository, UrbanResultService urbanResultService, FileStorageProperties fileStorageProperties) {
        this.videoRepository = videoRepository;
        this.customUserDetailsService = customUserDetailsService;
        this.urbanResultRepository = urbanResultRepository;
        this.urbanResultService = urbanResultService;
        this.videoStorageLocation = Paths.get(fileStorageProperties.getVideoDir())
                .toAbsolutePath().normalize();
        this.thumbnailStorageLocation = Paths.get(fileStorageProperties.getThumbnailDir())
                .toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.videoStorageLocation);
            Files.createDirectories(this.thumbnailStorageLocation);
        } catch (Exception ex) {
            throw new VideoStorageException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    Map<String, String> objectMap = Map.of(
            "사람", "person",
            "자전거", "bicycle",
            "차", "car",
            "오토바이", "motorcycle",
            "비행기", "airplane",
            "버스", "bus",
            "기차", "train",
            "트럭", "truck",
            "보트", "boat",
            "말", "horse"
    );

    public Video storeVideo(MultipartFile file) throws IOException, JCodecException {
        // 파일 이름 정규화
        String oriFileName = StringUtils.cleanPath(file.getOriginalFilename());

        Video video = new Video();
        video.setFileName(oriFileName);
        video.setFileType(file.getContentType());

        videoRepository.save(video);

        // file path setting
        String ext = oriFileName.substring(oriFileName.lastIndexOf(".") + 1);
        String videoFileName = video.getId() + "." + ext;

        Path videoFilePath = this.videoStorageLocation.resolve(videoFileName).normalize();
        Files.copy(file.getInputStream(), videoFilePath, StandardCopyOption.REPLACE_EXISTING);

        video.setFilePath(videoFilePath.toString());

        /*
            후에 경로 및 썸네일 수정 할 것
            썸네일은 여러 예제를 제시해줄 수 있도록 해야하는게 좋을듯 함
        */
        // video thumbnail 만들기
        String thumbnailFileName = video.getId() + ".png";
        Path thumbnailFilePath = this.thumbnailStorageLocation.resolve(thumbnailFileName).normalize();

        File videoFile = new File(video.getFilePath());
        File thumbnailFile = new File(thumbnailFilePath.toString());

        Picture picture = FrameGrab.getFrameFromFile(videoFile, 0);

        BufferedImage bufferedImage = AWTUtil.toBufferedImage(picture);
        ImageIO.write(bufferedImage, "png", thumbnailFile);
        try {
            FFmpegFrameGrabber grabber = FFmpegFrameGrabber.createDefault(videoFile);
            grabber.start();
            int length = grabber.getLengthInFrames();
            int fps = (int)grabber.getVideoFrameRate();
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            video.setMaxFrame(length);
            video.setFps(fps);
            video.setWidth(width);
            video.setHeight(height);
            System.out.println(length + " " + fps);
            grabber.close();
        } catch (Exception e) {
            // TODO Auto-generated catch block
        }

        video.setThumbnailPath(thumbnailFilePath.toString());

        return videoRepository.save(video);
    }

    public Video addDetailsFile(VideoRequest videoRequest, String writer) throws ParseException {
        User user = customUserDetailsService.getUser(writer);
        Video video = getFile(videoRequest.getFileId());
        video.setFileName(videoRequest.getFileName());
        video.setWriter(user);
        video.setPrivacyStatus(videoRequest.isPrivacyStatus());

        // update 후 UrbanAi 에 분석 요청
        URI uri = UriComponentsBuilder
                .fromUriString("http://api.urbanai.net:8000")
                .path("/type/{fileType}/inference/{model}")
                .encode()
                .build()
                .expand("video", "fast")
                .toUri();

        String videoUri = "http://202.31.147.195:7778/api/video/downloadFile/" + video.getId();

        HttpHeaders headers = new HttpHeaders();
        headers.set("uri", videoUri);

        HttpEntity request = new HttpEntity(headers);
        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, request, String.class);

        JSONParser jsonParser = new JSONParser();
        JSONObject jsonObject = (JSONObject) jsonParser.parse(response.getBody());

        System.out.println(jsonObject.toJSONString());

        System.out.println(jsonObject.get("task_id"));
        String id = (String) jsonObject.get("task_id");
        UrbanResult urbanResult = new UrbanResult(id, video);
        video.setResult(urbanResult);


        videoRepository.save(video);
        urbanResultRepository.save(urbanResult);

        return video;
    }

    public Video updateFileData(VideoRequest videoRequest) {
        Video video = getFile(videoRequest.getFileId());
        video.setFileName(videoRequest.getFileName());
        video.setPrivacyStatus(videoRequest.isPrivacyStatus());
        return videoRepository.save(video);
    }

    public void deleteFile(String videoId) {
        videoRepository.deleteById(videoId);
    }

    public ResponseEntity<List<VideoDto>> searchVideo(String content) {
        try {
            String[] searchList = content.split(",");
            List<VideoDto> videos = new ArrayList<>();

            for (Video video : videoRepository.findAll()) {
                int check = 0;
                UrbanResult urbanResult = video.getResult();
                if(content != ""){
                    for(int i = 0; i < searchList.length; i++){
                        String[] searchTarget = searchList[i].split(" ");

                        if(searchTarget.length == 1 && urbanResult.getObjectList() != null){
                            if(urbanResult.getObjectList() != null && objectMap.get(searchTarget[0]) != null && urbanResult.getObjectList().contains(objectMap.get(searchTarget[0])) || video.getTag() != null && video.getTag().contains(searchTarget[0])){
                                check++;
                            }
                        }
                        if(searchTarget.length == 2 && urbanResult.getObjectList() != null){
                            JSONObject jsonObject = urbanResultService.getChartData(video, 1, objectMap.get(searchTarget[0]));
                            JSONArray target = (JSONArray) jsonObject.get("objectNum");
                            for (int j = 0; j < target.size(); j++) {
                                String num = String.valueOf(target.get(j));
                                double num1 = Double.parseDouble(num);
                                if ((int) num1 - 1 >= Integer.parseInt(searchTarget[1]) && (int) num1 + 1 >= Integer.parseInt(searchTarget[1])) {
                                    check++;
                                    break;
                                }
                            }
                        }
                    }
                }
                if(check == searchList.length||content == ""){
                    VideoDto videoDto = new VideoDto();
                    videoDto.setId(video.getId());
                    videoDto.setFileType(video.getFileType());
                    videoDto.setFileName(video.getFileName());
                    videoDto.setPrivacyStatus(video.isPrivacyStatus());
                    videos.add(videoDto);
                }
            }
            if (videos.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<>(videos, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
//
//    public ResponseEntity<List<VideoDto>> searchVideoAs(String content){
//        try {
//            String[] searchList = content.split(",");
//            List<VideoDto> videos = new ArrayList<VideoDto>();
//            for (Video video : videoRepository.findAll()) {
//                if(video.isPrivacyStatus() && (video.getFileName().contains(content) || video.getTag() != null && video.getTag().contains(content))) {
//                    VideoDto videoDto = new VideoDto();
//                    videoDto.setId(video.getId());
//                    videoDto.setFileType(video.getFileType());
//                    videoDto.setFileName(video.getFileName());
//                    videoDto.setPrivacyStatus(video.isPrivacyStatus());
//                    videos.add(videoDto);
//                }
////                else {
////                    String[] contentList = content.split(" ");
////                    int check = 0;
////                    for(int i = 0; i < contentList.length; i++){
////                        if(video.getObjList() != null && video.getObjList().contains(objectMap.get(contentList[i]))){
////                            check++;
////                        }
////                    }
////                    if(check == contentList.length){
////                        VideoDto videoDto = new VideoDto();
////                        videoDto.setId(video.getId());
////                        videoDto.setFileType(video.getFileType());
////                        videoDto.setFileName(video.getFileName());
////                        videoDto.setPrivacyStatus(video.isPrivacyStatus());
////                        videos.add(videoDto);
////                    }
////                    System.out.println(check + " " + contentList.length);
////                }
//            }
//            if (videos.isEmpty()) {
//                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
//            }
//            return new ResponseEntity<>(videos, HttpStatus.OK);
//        } catch (Exception e) {
//            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
//        }
//    }

    public Video getFile(String fileId) {
        return videoRepository.findById(fileId)
                .orElseThrow(() -> new VideoNotFoundException("File not found with id " + fileId));
    }

    public Resource loadFileAsResource(String fileId) {
        Video video = videoRepository.findById(fileId)
                .orElseThrow(() -> new VideoNotFoundException("File not found with id " + fileId));
        try {
            Path filePath = Path.of(video.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return resource;
            } else {
                throw new VideoNotFoundException("File not found " + fileId);
            }
        } catch (MalformedURLException ex) {
            throw new VideoNotFoundException("File not found " + fileId, ex);
        }
    }

}

