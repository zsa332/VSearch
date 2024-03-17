package com.example.server.controller;

import com.example.server.dto.SearchDto;
import com.example.server.dto.SearchTimeDto;
import com.example.server.dto.VideoDto;
import com.example.server.entity.UrbanResult;
import com.example.server.entity.Video;
import com.example.server.service.UrbanResultService;
import com.example.server.service.VideoService;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/urban-ai")
public class UrbanResultController {

    private final UrbanResultService urbanResultService;

    private final VideoService videoService;

    public UrbanResultController(UrbanResultService urbanResultService, VideoService videoService) {
        this.urbanResultService = urbanResultService;
        this.videoService = videoService;
    }

//    @GetMapping("/urban-result")
//    public String getUrbanResult() throws ParseException {
//        String status = urbanResultService.getUrbanResult();
//
//        return status;
//    }

//    @GetMapping("/fileInfo/{videoId}")
//    public ResponseEntity<?> getFileInfo(@PathVariable String videoId){
//       JSONObject jsonObject = urbanResultService.fileInfo(videoId);
//        return new ResponseEntity<>(jsonObject, HttpStatus.OK);
//    }

//    @GetMapping("/object-list/{videoId}")
//    public ResponseEntity<?> getObjectList(@PathVariable String videoId) throws ParseException {
//
//        return new ResponseEntity<>(jsonObject, HttpStatus.OK);
//    }

    @PostMapping("/string-search")
    public ResponseEntity<List<VideoDto>> searchVideo(@RequestBody SearchDto searchDto){
        long startTime = System.currentTimeMillis();
        List<VideoDto> videoDtoList = urbanResultService.searchStringObject(searchDto.getContent());
        long stopTime = System.currentTimeMillis();
        System.out.println(stopTime - startTime);
        if(videoDtoList.isEmpty()){
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(videoDtoList, HttpStatus.OK);
    }
    @PostMapping("/bit-search")
    public ResponseEntity<List<VideoDto>> bitSearchVideo(@RequestBody SearchDto searchDto){
        long startTime = System.currentTimeMillis();
        List<VideoDto> videoDtoList = urbanResultService.searchBitObject(searchDto.getContent());
        long stopTime = System.currentTimeMillis();
        if(videoDtoList.isEmpty()){
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(videoDtoList, HttpStatus.OK);
    }

    @PostMapping("/bitnum-search")
    public ResponseEntity<List<VideoDto>> bitNumSearchVideo(@RequestBody SearchDto searchDto){
        long startTime = System.currentTimeMillis();
        List<VideoDto> videoDtoList = urbanResultService.searchBitNumObject(searchDto.getContent());
        long stopTime = System.currentTimeMillis();
        if(videoDtoList.isEmpty()){
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(videoDtoList, HttpStatus.OK);
    }

    @PostMapping("/final-search")
    public ResponseEntity<List<VideoDto>> SearchVideoFinal(@RequestBody SearchDto searchDto){
        List<VideoDto> videoDtoList = urbanResultService.searchVideoListFinal(searchDto.getContent());
        if(videoDtoList.isEmpty()){
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        return new ResponseEntity<>(videoDtoList, HttpStatus.OK);
    }

    @PostMapping("/time-search")
    public ResponseEntity<JSONObject> SearchVideoFinal(@RequestBody SearchTimeDto searchTimeDto){
        JSONObject jsonObject = urbanResultService.searchVideoTime(searchTimeDto.getId(), searchTimeDto.getContent());

        return new ResponseEntity<>(jsonObject, HttpStatus.OK);
    }

    @GetMapping("/object-list/{videoId}")
    public ResponseEntity<?> getObjectList(@PathVariable String videoId) throws ParseException {
        Video video = videoService.getFile(videoId);
        UrbanResult urbanResult = video.getResult();
        JSONObject jsonObject = new JSONObject();
        if(urbanResult.getStatus() == null || !urbanResult.getStatus().contains("SUCCESS")){
            urbanResultService.getUrbanResult(video);

        }
        if(urbanResult.getStatus().contains("SUCCESS")){
            jsonObject = urbanResultService.getObjectList(video);
        }

        return new ResponseEntity<>(jsonObject, HttpStatus.OK);
    }

    @GetMapping("/graph/{videoId}")
    public ResponseEntity<?> getGraphData(@PathVariable String videoId, @RequestParam int maxX, @RequestParam int maxY, @RequestParam String objectName){
        Video video = videoService.getFile(videoId);
        UrbanResult urbanResult = video.getResult();
        JSONObject jsonObject = new JSONObject();
        if(urbanResult.getStatus() == null || !urbanResult.getStatus().contains("SUCCESS")){
            urbanResultService.getUrbanResult(video);
        }
        if(urbanResult.getStatus().contains("SUCCESS")){
            jsonObject = urbanResultService.getGraphData(video, maxX, maxY, objectName);
        }
        return new ResponseEntity<>(jsonObject, HttpStatus.OK);
    }

    @GetMapping("/chart/{videoId}")
    public ResponseEntity<?> getChartData(@PathVariable String videoId, @RequestParam int timeSet, @RequestParam String objectName) throws ParseException {
        Video video = videoService.getFile(videoId);
        UrbanResult urbanResult = video.getResult();
        JSONObject jsonObject = new JSONObject();
        if(urbanResult.getStatus() == null || !urbanResult.getStatus().contains("SUCCESS")){
            urbanResultService.getUrbanResult(video);
        }
        if(urbanResult.getStatus().contains("SUCCESS")){
            jsonObject = urbanResultService.getChartData(video, timeSet, objectName);
        }

        return new ResponseEntity<>(jsonObject, HttpStatus.OK);
    }

    @GetMapping("/emerge-frame/{videoId}")
    public ResponseEntity<?> getEmergeFrame(@PathVariable String videoId, @RequestParam String objectName){
        Video video = videoService.getFile(videoId);
        JSONObject jsonObject = urbanResultService.getEmergeFrame(video, objectName);

        return new ResponseEntity<>(jsonObject, HttpStatus.OK);
    }


}
