package com.example.server.controller;



import com.example.server.dto.SearchDto;
import com.example.server.dto.VideoDto;
import com.example.server.dto.VideoRequest;
import com.example.server.entity.User;
import com.example.server.entity.Video;
import com.example.server.repository.VideoRepository;
import com.example.server.service.CustomUserDetailsService;
import com.example.server.service.VideoService;
import org.jcodec.api.JCodecException;
import org.jcodec.common.io.IOUtils;
import org.json.simple.parser.ParseException;
import org.springframework.core.io.FileUrlResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@RestController
@RequestMapping("/api/video")
public class VideoController {

    private final VideoService videoService;

    private final CustomUserDetailsService customUserDetailsService;

    private final VideoRepository videoRepository;

//    private final RestTemplateService restTemplateService;

    public VideoController(VideoService videoService, CustomUserDetailsService customUserDetailsService, VideoRepository videoRepository) {
        this.videoService = videoService;
        this.customUserDetailsService = customUserDetailsService;
        this.videoRepository = videoRepository;
//        this.restTemplateService = restTemplateService;
    }

    @GetMapping("/list")
    public ResponseEntity<List<VideoDto>> getPublicVideos(){
        try{
            List<VideoDto> videos = new ArrayList<VideoDto>();
            for(Video video : videoRepository.findAll()){
                if(video.isPrivacyStatus()){
                    VideoDto videoDto = new VideoDto();
                    videoDto.setId(video.getId());
                    videoDto.setFileType(video.getFileType());
                    videoDto.setFileName(video.getFileName());
                    videoDto.setPrivacyStatus(video.isPrivacyStatus());
                    videos.add(videoDto);
                }
            }
            if(videos.isEmpty()){
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<>(videos, HttpStatus.OK);
        } catch(Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/list/{username}")
    public ResponseEntity<List<VideoDto>> getUserVideos(@PathVariable String username){
        User user = customUserDetailsService.getUser(username);
        List<Video> videos = user.getVideos();
        List<VideoDto> videoDtos = new ArrayList<>();
        for(Video video : videos){
            VideoDto videoDto = new VideoDto();
            videoDto.setId(video.getId());
            videoDto.setFileType(video.getFileType());
            videoDto.setFileName(video.getFileName());
            videoDto.setPrivacyStatus(video.isPrivacyStatus());
            videoDtos.add(videoDto);
        }
        if(videos.isEmpty()){
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(videoDtos, HttpStatus.OK);
    }

    @GetMapping("/admin-list")
    public ResponseEntity<List<VideoDto>> getAdminVideos(){
        try{
            List<VideoDto> videos = new ArrayList<VideoDto>();
            for(Video video : videoRepository.findAll()){
                VideoDto videoDto = new VideoDto();
                videoDto.setId(video.getId());
                videoDto.setFileType(video.getFileType());
                videoDto.setFileName(video.getFileName());
                videoDto.setPrivacyStatus(video.isPrivacyStatus());
                videos.add(videoDto);
            }
            if(videos.isEmpty()){
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<>(videos, HttpStatus.OK);
        } catch(Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value = "/thumbnail/{fileId:.+}", produces = "image/png")
    public ResponseEntity<byte[]> getThumbnail(@PathVariable String fileId) throws IOException {
        Video video = videoService.getFile(fileId);

        InputStream imageStream = new FileInputStream(video.getThumbnailPath());

        byte[] data = IOUtils.toByteArray(imageStream);
        imageStream.close();

        return new ResponseEntity<>(data, HttpStatus.OK);
    }

    @GetMapping("/downloadFile/{fileId:.+}")
    public ResponseEntity <Resource> downloadFile(@PathVariable String fileId, HttpServletRequest request) {
        // Load file as Resource
        Resource resource = videoService.loadFileAsResource(fileId);

        // Try to determine file's content type
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            System.out.println("Could not determine file type.");
        }

        // Fallback to the default content type if type could not be determined
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/stream/{fileId:.+}")
    public ResponseEntity<ResourceRegion> streamVideo(@RequestHeader HttpHeaders headers, @PathVariable String fileId) throws IOException {
        Video video = videoService.getFile(fileId);

        FileUrlResource streamVideo = new FileUrlResource(video.getFilePath());
        ResourceRegion resourceRegion;

        final long chunkSize = 1000000L;
        long contentLength = streamVideo.contentLength();

        Optional<HttpRange> optional = headers.getRange().stream().findFirst();
        HttpRange httpRange;
        if (optional.isPresent()) {
            httpRange = optional.get();
            long start = httpRange.getRangeStart(contentLength);
            long end = httpRange.getRangeEnd(contentLength);
            long rangeLength = Long.min(chunkSize, end - start + 1);
            resourceRegion = new ResourceRegion(streamVideo, start, rangeLength);
        } else {
            long rangeLength = Long.min(chunkSize, contentLength);
            resourceRegion = new ResourceRegion(streamVideo, 0, rangeLength);
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaTypeFactory.getMediaType(streamVideo).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .body(resourceRegion);
    }

    @GetMapping("/addDe")
    public String addDe() throws ParseException {
        for(Video video : videoRepository.findAll()){
            System.out.println("addde");
            VideoRequest videoRequest = new VideoRequest();
            videoRequest.setFileId(video.getId());
            videoRequest.setPrivacyStatus(video.isPrivacyStatus());
            videoRequest.setFileName(video.getFileName());
            Video videoa = videoService.addDetailsFile(videoRequest, video.getWriter().getUsername());
        }

        return "success";
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadVideo(@RequestParam MultipartFile file) throws JCodecException, IOException {
        if(file.getContentType().startsWith("video") == false){
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        Video video = videoService.storeVideo(file);

        return new ResponseEntity<>(new VideoDto(video.getId(), video.getFileName(), file.getContentType(),false), HttpStatus.OK);
    }

    @PostMapping("/search")
    public ResponseEntity<List<VideoDto>> searchVideo(@RequestBody SearchDto searchDto){
        return videoService.searchVideo(searchDto.getContent());
    }

//    @PostMapping("/searchAs")
//    public ResponseEntity<List<VideoDto>> searchVideoAs(@RequestBody SearchDto searchDto){
//        return videoService.searchVideoAs(searchDto.getContent());
//    }


    @PutMapping("/add-details")
    public VideoDto addVideoDetails(@RequestBody VideoRequest videoRequest, Principal principal) throws ParseException{
        System.out.println(videoRequest.toString());
        Video video = videoService.addDetailsFile(videoRequest, principal.getName());
        return new VideoDto(video.getId(), video.getFileName(), video.getFileType(), video.isPrivacyStatus());
    }

    @PutMapping("/update")
    public VideoDto updateVideoData(@RequestBody VideoRequest videoRequest){
        Video video = videoService.updateFileData(videoRequest);
        return new VideoDto(video.getId(), video.getFileName(), video.getFileType(), video.isPrivacyStatus());
    }

    @DeleteMapping("/delete/{videoId}")
    public ResponseEntity<String> deleteVideo(@PathVariable String videoId){
        videoService.deleteFile(videoId);
        return new ResponseEntity<>("Success", HttpStatus.OK);
    }


}
