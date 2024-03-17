package com.example.server.service;

import com.example.server.dto.VideoDto;
import com.example.server.entity.*;
import com.example.server.exception.VideoNotFoundException;
import com.example.server.repository.UrbanMapping;
import com.example.server.repository.UrbanResultRepository;
import com.example.server.repository.VideoRepository;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UrbanResultService {

    private final UrbanResultRepository urbanResultRepository;

    private final VideoRepository videoRepository;

    private final ObjectClassService objectClassService;

    public UrbanResultService(UrbanResultRepository urbanResultRepository, VideoRepository videoRepository, ObjectClassService objectClassService) {
        this.urbanResultRepository = urbanResultRepository;
        this.videoRepository = videoRepository;
        this.objectClassService = objectClassService;
    }

    public void getUrbanResult(Video video) {
        UrbanResult urbanResult = video.getResult();

        URI uri = UriComponentsBuilder
                .fromUriString("http://api.urbanai.net:8000")
                .path("/search/{taskId}")
                .encode()
                .build()
                .expand(urbanResult.getId())
                .toUri();

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

        try {
            JSONParser jsonParser = new JSONParser();
            JSONObject jsonObject = (JSONObject) jsonParser.parse(response.getBody());

            urbanResult.setStatus((String) jsonObject.get("task_status"));

            if (urbanResult.getStatus().equals("SUCCESS")) {

                //  result 가 [[{}]]로 되어있기 때문에 껍대기 제거
                JSONArray taskResult = (JSONArray) jsonObject.get("task_result");
                JSONArray taskResult1 = (JSONArray) taskResult.get(0);
                JSONObject taskResult2 = (JSONObject) taskResult1.get(0);

                //  객체 ID
                JSONArray objectIds = (JSONArray) taskResult2.get("object_id");
                urbanResult.setObjectId(objectIds);

                //  객체 이름
                JSONArray classNames = (JSONArray) taskResult2.get("class_name");
                urbanResult.setClassName(classNames);

                //  객체 출현 프레임
                JSONArray frameIds = (JSONArray) taskResult2.get("frame_id");
                urbanResult.setFrameId(frameIds);

                //  객체 정확도
                JSONArray scores = (JSONArray) taskResult2.get("score");
                urbanResult.setScore(scores);

                //  객체 좌표
                JSONArray locations = (JSONArray) taskResult2.get("itraj");
                urbanResult.setLocation(locations);

                //  객체 bounding box
                JSONArray bboxs = (JSONArray) taskResult2.get("bbox");
                urbanResult.setBbox(bboxs);

                setFrameByObjectBitList(urbanResult);
                setFrameByObjectBitNumList(urbanResult);
                urbanResultRepository.save(urbanResult);
            }
        }catch (ParseException e){
            System.out.println("ParseException : " + e);
        }
    }

    public JSONObject getObjectList(Video video){
        UrbanResult urbanResult = video.getResult();

        JSONObject jsonObject = new JSONObject();
        //  object list 가 null 이면 채워서 response
        if(urbanResult.getObjectList() == null){
            setObjList(urbanResult);
        }
        String objStr = urbanResult.getObjectList().toString().substring(1,urbanResult.getObjectList().toString().length()-1);
        objStr = objStr.replaceAll("\"", "");
        String[] objArray = objStr.split(", ");
        jsonObject.put("objectList", objArray);
        jsonObject.put("maxFrame", video.getMaxFrame());
        jsonObject.put("fps", video.getFps());
        jsonObject.put("title", video.getFileName());
        jsonObject.put("tag", video.getTag());

        return jsonObject;
    }

    public void setObjList(UrbanResult urbanResult){
        String classStr = "person,bicycle,car,motorcycle,airplane,bus,train,truck,boat,bench,bird,cat,dog,horse,bear,umbrella,bottle,cup,pizza,donut,cake,chair,couch,tv,laptop,teddy bear";
        try{
            if(urbanResult.getStatus() != null && urbanResult.getStatus().contains("SUCCESS")){
                List<String> objList = urbanResult.getClassName();
                Set<String> objectSet = new HashSet<>();
                List<String> accArr = urbanResult.getScore();
                //  객체 정확도가 0.94 이상인 경우 추가
                for(int i = 0; i < accArr.size(); i++){
                    String tempAcc = accArr.get(i).substring(1, accArr.get(i).length()-1);
                    String[] acc = tempAcc.split(",");
                    for(int j = 0; j < acc.length; j++){
                        if(Double.parseDouble(acc[j]) > 0.94 && !objectSet.contains(objList.get(i)) && classStr.contains(objList.get(i))){
                            objectSet.add(objList.get(i));
                            break;
                        }
                    }
                }
                List<String> resultList = new ArrayList<>(objectSet);

                urbanResult.setObjectList(resultList);
                urbanResultRepository.save(urbanResult);
            }

        }catch(Exception e){
            System.out.println("error : " + e);
        }
    }

    public JSONObject getGraphData(Video video, int maxX, int maxY, String objectName){
        UrbanResult urbanResult = video.getResult();
        int width = video.getWidth();
        int height = video.getHeight();

        List<Integer> objId = urbanResult.getObjectId();

        List<String> objList = urbanResult.getClassName();

        List<String> objLoc = urbanResult.getLocation();

        List<String> frame = urbanResult.getFrameId();


        double[][] graphMap = new double[maxX][maxY];
        String[][] frameMap = new String[maxX][maxY];
        String[][] checkMap = new String[maxX][maxY];

        int boundaryX = width/maxX;
        int boundaryY = height/maxY;
        int fps = video.getFps();

        for(int i = 0; i < objId.size(); i++){
            if(objList.get(i).contains(objectName) || objectName.contains("total")){
                String id = String.valueOf(objId.get(i));
                String tempFrame = frame.get(i).substring(1, frame.get(i).length()-1);
                String tempLoc = objLoc.get(i).substring(2, objLoc.get(i).length()-2);
                String[] frameByObj = tempFrame.split(",");
                String[] LocByObj = tempLoc.split("],\\[");

                for(int j = 0; j < frameByObj.length; j++){
                    String[] objXY = LocByObj[j].split(",");
                    int x = Integer.parseInt(objXY[0]);
                    int y = Integer.parseInt(objXY[1]);
                    if(y > height) y = height;

                    x = x/boundaryX;
                    y = y/boundaryY;
                    if(x >= maxX) x = maxX-1;
                    if(y >= maxY) y = maxY-1;

                    graphMap[y][x] = graphMap[y][x] + (double)1/ (double)fps;
                    if(frameMap[y][x] == null){
                        frameMap[y][x] = "[["+frameByObj[j]+"]";
                        checkMap[y][x] = id;
                    }
                    else if(checkMap[y][x]!=id){
                        frameMap[y][x] += ",["+frameByObj[j]+"]";
                    }
                    else{
                        frameMap[y][x] = frameMap[y][x].substring(0, frameMap[y][x].length()-1);
                        frameMap[y][x] += ","+frameByObj[j]+"]";
                    }
                }
            }
        }
//
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray = new JSONArray();

        jsonObject.put("maxFrame", video.getMaxFrame());


        int id = 0;
        for (int i= 0; i < maxX; i++){
            for(int j = 0; j < maxY; j++){
                JSONObject jsonSub = new JSONObject();
                JSONObject dataJson = new JSONObject();
                if(frameMap[i][j] != null)frameMap[i][j] += "]";
                jsonSub.put("group", "nodes");
                dataJson.put("id", ++id);
                dataJson.put("node_weight", graphMap[i][j]);
                dataJson.put("opacity", graphMap[i][j] < 1 ? graphMap[i][j] : 1);
                dataJson.put("frameId",frameMap[i][j]);
                jsonSub.put("data", dataJson);
                jsonArray.add(jsonSub);
            }
        }

        jsonObject.put("graphData", jsonArray);

        return jsonObject;
    }

    public JSONObject getChartData(Video video, int timeSet, String objectName) {
        UrbanResult urbanResult = video.getResult();

        List<String> objList = urbanResult.getClassName();
        List<String> objLoc = urbanResult.getLocation();
        List<String> frame = urbanResult.getFrameId();

        int maxFrame = video.getMaxFrame();
        int fps = video.getFps();
        System.out.println(timeSet);
        int frameBySec = timeSet * fps;
        System.out.println(frameBySec);
        int chartLength = maxFrame / frameBySec + 1;

        int[] countObjByFrame = new int[maxFrame+1];
        int[] objectMoveDistance = new int[maxFrame+1];

        for (int i = 0; i < objList.size(); i++) {
            if (objList.get(i).contains(objectName)) {
                String tempFrame = frame.get(i).substring(1, frame.get(i).length() - 1);
                String tempLoc = objLoc.get(i).substring(2, objLoc.get(i).length() - 2);

                String[] frameByObj = tempFrame.split(",");
                String[] LocByObj = tempLoc.split("],\\[");

                for (int j = 0; j < frameByObj.length; j++) {
                    int idx = Integer.parseInt(frameByObj[j]);
                    countObjByFrame[idx]++;

                    if (j > 0) {
                        String[] beforeXY = LocByObj[j - 1].split(",");
                        int beforeX = Integer.parseInt(beforeXY[0]);
                        int beforeY = Integer.parseInt(beforeXY[1]);
                        String[] currentXY = LocByObj[j].split(",");
                        int currentX = Integer.parseInt(currentXY[0]);
                        int currentY = Integer.parseInt(currentXY[1]);

                        int distance = Math.abs(beforeX - currentX) + Math.abs(beforeY - currentY);
                        objectMoveDistance[idx] += distance;
                    }
                }
            }
        }

        int[] objectNum = new int[chartLength];
        int[] objectSpeed = new int[chartLength];
        int idx = 0;
        for (int i = 0; i < maxFrame; i++) {
            objectNum[idx] = objectNum[idx]+countObjByFrame[i];
            objectSpeed[idx] = objectSpeed[idx]+objectMoveDistance[i];
            if (i != 0 && i % frameBySec == 0){
                if(objectNum[idx] > 0 && objectNum[idx] < frameBySec)objectNum[idx] = 1;
                else objectNum[idx] = objectNum[idx]/frameBySec/2;
                if(objectNum[idx]!= 0)objectSpeed[idx]= objectSpeed[idx]/frameBySec/objectNum[idx];
                else objectSpeed[idx] = 0;
                idx++;
            }
            else if(i == maxFrame-1){
                if(objectNum[idx] > 0 && objectNum[idx] < (i%frameBySec))objectNum[idx] = 1;
                else objectNum[idx] = objectNum[idx]/(i%frameBySec);
                if(objectNum[idx]!= 0)objectSpeed[idx]= objectSpeed[idx]/(i%frameBySec)/objectNum[idx];
                else objectSpeed[idx] = 0;
                idx++;
            }
        }

        String[] timeList = new String[chartLength];
//        JSONArray objectNumJson = new JSONArray();
        int min = 0, sec = 0;
        for (int i = 0; i < chartLength; i++) {
            if (sec % 60 == 0 && sec != 0) {
                min++;
                sec = 0;
            }
            if (sec % timeSet == 0) {
                timeList[i] = Integer.toString(min) + ":" + Integer.toString(sec);
            }
            sec = sec + timeSet;
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("objectNum", objectNum);
        jsonObject.put("objectSpeed", objectSpeed);
        jsonObject.put("timeList", timeList);

        return jsonObject;
    }

    public JSONObject getEmergeFrame(Video video, String objectName){
        UrbanResult urbanResult = video.getResult();

        List<String> objList = urbanResult.getClassName();
        List<String> frame = urbanResult.getFrameId();

        Set<Integer> emergeFrame = new HashSet<>();

        for(int i = 0; i < objList.size(); i++){
            if(objList.get(i).contains(objectName)){
                String tempFrame = frame.get(i).substring(1, frame.get(i).length()-1);
                String[] frameByObj = tempFrame.split(",");

                for(int j = 0; j < frameByObj.length; j++){
                    emergeFrame.add(Integer.parseInt(frameByObj[j]));
                }
            }
        }

        List<Integer> frameList = new ArrayList(emergeFrame);
        Collections.sort(frameList);

        List<List<Integer>> result = new ArrayList<>();

        int start = 0, end = 0;
        for(int i = 0; i < frameList.size(); i++){
            if(i > 0 && (Math.abs(frameList.get(i-1) - frameList.get(i)) > 2 || i+1 == frameList.size())){
                List<Integer> sub = frameList.subList(start, end);
                result.add(sub);
                start = i;
            }
            end++;
        }

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("emergeFrame", result);

        return jsonObject;
    }

    public List<VideoDto> searchStringObject(String content){
        content = content.replaceAll(" ", "");
        String[] searchArr = content.split(",");
        Arrays.sort(searchArr);
        String search = "";
        for(int i = 0; i < searchArr.length; i++){
            search += searchArr[i] + ",";
        }
        search =search.substring(0, search.length()-1);
        System.out.println(search);
        List<VideoDto> videoDtoList = new ArrayList<>();
        for(UrbanMapping urbanMapping : urbanResultRepository.findSearchString(search)){
            VideoMapping video = videoRepository.findByUrban(urbanMapping.getVid());
            VideoDto videoDto = new VideoDto();
            videoDto.setFileName(video.getFileName());
            videoDto.setId(video.getId());
            videoDto.setFileType(video.getFileType());
            videoDto.setPrivacyStatus(true);
            videoDtoList.add(videoDto);
        }
        return videoDtoList;
    }

    public void setFrameByObjectList(){
        String classStr = "person,bicycle,car,motorcycle,airplane,bus,train,truck,boat,bench,bird,cat,dog,horse,bear,umbrella,bottle,cup,pizza,donut,cake,chair,couch,tv,laptop,teddy bear";
        for(UrbanResult urbanResult : urbanResultRepository.findAll()){
            Video video = urbanResult.getVideo();
            int fps = video.getFps();
            int maxFrame = video.getMaxFrame();
            int size = maxFrame/fps+1;
            List<String> objList = urbanResult.getClassName();
            List<String> frame = urbanResult.getFrameId();
            String[] fobject = new String[size];
            Arrays.fill(fobject, "");
            for(int i = 0; i < objList.size(); i++){
                if(classStr.contains(objList.get(i))){
                    String tempFrame = frame.get(i).substring(1, frame.get(i).length()-1);
                    String[] frameByObj = tempFrame.split(",");
                    int idx = Integer.parseInt(frameByObj[0]) / fps;
                    for(int j = 0; j < frameByObj.length; j+=fps) {
                        if(!fobject[idx].contains(objList.get(i)))fobject[idx]+= objList.get(i)+",";
                        idx++;
                    }
                }
            }
            List<String> result = new ArrayList<>();

            for(int i = 0; i < fobject.length; i++){
                if(fobject[i].length() !=0){
                    fobject[i] = fobject[i].substring(0, fobject[i].length()-1);
                }
                String[] fob = fobject[i].split(",");
                Arrays.sort(fob);
                String addValue = "";
                for(int j = 0; j < fob.length;j++){
                    addValue+=fob[j] + ",";
                }
                addValue = addValue.substring(0, addValue.length()-1);
                result.add(addValue);
            }
            System.out.println(result.get(0));
            urbanResult.setFrameByObjStr(result);
            urbanResultRepository.save(urbanResult);
        }
    }

//    public void setFrameByObjectList(){
//        String classStr = "person,bicycle,car,motorcycle,airplane,bus,train,truck,boat,bench,bird,cat,dog,horse,bear,umbrella,bottle,cup,pizza,donut,cake,chair,couch,tv,laptop,teddy bear";
//        for(UrbanResult urbanResult : urbanResultRepository.findAll()){
//            Video video = urbanResult.getVideo();
//            int fps = video.getFps();
//            List<String> objList = urbanResult.getClassName();
//            Set<String> set = new HashSet<>();
//            for(int i = 0; i < objList.size(); i++){
//                if(classStr.contains(objList.get(i))) {
//                    set.add(objList.get(i));
//                }
//            }
//            List<String> result = new ArrayList<>(set);
//            Collections.sort(result);
//
//            urbanResult.setObjectList(result);
//            urbanResultRepository.save(urbanResult);
//        }
//    }

    public List<VideoDto> searchBitObject(String content){
        List<VideoDto> videoDtoList = new ArrayList<>();
        content = content.replaceAll(" ", "");
        String[] searchArr = content.split(",");
        int bitNum = 0;
        for(int i = 0; i < searchArr.length; i++){
            ObjectClass objectClass = objectClassService.getObject(searchArr[i]);

            bitNum = bitNum|objectClass.getBinaryInt();
            System.out.println("num : " + objectClass.getBinaryInt() + " bitNum : "+ bitNum + " bit:" + Integer.toBinaryString(bitNum));
        }
        System.out.println(bitNum);
        for(UrbanMapping urbanMapping : urbanResultRepository.findSearchBit(bitNum)){
            VideoMapping video = videoRepository.findByUrban(urbanMapping.getVid());
            VideoDto videoDto = new VideoDto();
            videoDto.setFileName(video.getFileName());
            videoDto.setId(video.getId());
            videoDto.setFileType(video.getFileType());
            videoDto.setPrivacyStatus(true);
            videoDtoList.add(videoDto);
        }
        return videoDtoList;
    }

    public void setFrameByObjectBitList(UrbanResult urbanResult){
        String classStr = "person,bicycle,car,motorcycle,airplane,bus,train,truck,boat,bench,bird,cat,dog,horse,bear,umbrella,bottle,cup,pizza,donut,cake,chair,couch,tv,laptop,teddy bear";
        String[] classArr = classStr.split(",");
        Video video = urbanResult.getVideo();
        int fps = video.getFps();
        int maxFrame = video.getMaxFrame();
        int size = maxFrame/fps+1;
        List<String> objList = urbanResult.getClassName();
        List<String> frame = urbanResult.getFrameId();
        int[] frameBit = new int[size];

        for(int i = 0; i < objList.size(); i++){
            if(classStr.contains(objList.get(i))){
                String tempFrame = frame.get(i).substring(1, frame.get(i).length()-1);
                String[] frameByObj = tempFrame.split(",");
                int idx = Integer.parseInt(frameByObj[0]) / fps;
                for(int j = 0; j < frameByObj.length; j+=fps) {
                    ObjectClass objectClass = objectClassService.getObject(objList.get(i));
                    frameBit[idx] = frameBit[idx] | objectClass.getBinaryInt();
                    idx++;
                }
            }
        }

        List<Integer> result = Arrays.stream(frameBit).boxed().collect(Collectors.toList());
        urbanResult.setFrameByObj(result);
        urbanResultRepository.save(urbanResult);
    }

    public List<VideoDto> searchBitNumObject(String content){
        String classStr = "person,bicycle,car,motorcycle,airplane,bus,train,truck,boat,bench,bird,cat,dog,horse,bear,umbrella,bottle,cup,pizza,donut,cake,chair,couch,tv,laptop,teddy bear";
        String[] classArr = classStr.split(",");
        List<VideoDto> videoDtoList = new ArrayList<>();
        String targetName = content.replaceAll("[0-9 ]", "");
        String[] searchName = targetName.split(",");
        String targetNum = content.replaceAll("[^0-9,]","");
        String[] searchNum = targetNum.split(",");
        int[] searchArr = new int[26];
        for(int i = 0; i < searchName.length; i++){
            int loc = Arrays.asList(classArr).indexOf(searchName[i]);
            searchArr[loc] = Integer.parseInt(searchNum[i]);
        }
        for(UrbanMapping urbanMapping : urbanResultRepository.findSearchBitNum(searchArr)){
            VideoMapping video = videoRepository.findByUrban(urbanMapping.getVid());
            VideoDto videoDto = new VideoDto();
            videoDto.setFileName(video.getFileName());
            videoDto.setId(video.getId());
            videoDto.setFileType(video.getFileType());
            videoDto.setPrivacyStatus(true);
            videoDtoList.add(videoDto);
        }
        System.out.println(targetName);
        System.out.println(targetNum);
        return videoDtoList;
    }
    public void setFrameByObjectBitNumList(UrbanResult urbanResult){
        String classStr = "person,bicycle,car,motorcycle,airplane,bus,train,truck,boat,bench,bird,cat,dog,horse,bear,umbrella,bottle,cup,pizza,donut,cake,chair,couch,tv,laptop,teddy bear";
        String[] classArr = classStr.split(",");
            Video video = urbanResult.getVideo();
            int fps = video.getFps();
            int maxFrame = video.getMaxFrame();
            int size = maxFrame/fps+1;
            List<String> objList = urbanResult.getClassName();
            List<String> frame = urbanResult.getFrameId();
            Map<Integer, int[]> frameMap = new HashMap<>();
            for(int i = 0; i < size; i++){
                int[] temp = new int[26];
                frameMap.put(i, temp);
            }
            for(int i = 0; i < objList.size(); i++){
                if(classStr.contains(objList.get(i))){
                    String tempFrame = frame.get(i).substring(1, frame.get(i).length()-1);
                    String[] frameByObj = tempFrame.split(",");
                    int idx = Integer.parseInt(frameByObj[0]) / fps;
                    for(int j = 0; j < frameByObj.length; j+=fps) {
                        int loc = Arrays.asList(classArr).indexOf(objList.get(i));
                        int[] frameArr= frameMap.get(idx);
                        frameArr[loc] += 1;
                        frameMap.put(idx, frameArr);
                        idx++;
                    }
                }
            }

            System.out.println(Arrays.toString(frameMap.get(1)));
//            break;
            List<String> result = new ArrayList<>();
            Iterator<Integer> keys = frameMap.keySet().iterator();
            while( keys.hasNext() ){
                int key = keys.next();
                String value = Arrays.toString(frameMap.get(key));
                result.add(value);
            }
            System.out.println(Arrays.toString(result.toArray()));
            urbanResult.setFrameByObjBitNum(result);
            urbanResultRepository.save(urbanResult);
    }

//    public void setObjectClasses(){
//        String classStr = "person,bicycle,car,motorcycle,airplane,bus,train,truck,boat,bench,bird,cat,dog,horse,bear,umbrella,bottle,cup,pizza,donut,cake,chair,couch,tv,laptop,teddy bear";
//        String[] classArr = classStr.split(",");
//        String korClassStr = "사람, 자전거, 자동차, 오토바이, 비행기, 버스, 기차, 트럭, 보트, 벤치, 새, 고양이, 개, 말, 곰, 우산, 병, 컵,피자,도넛,케익,의자,소파,tv,노트북,테디베어";
//        korClassStr = korClassStr.replaceAll(" ","");
//        String[] korArr = korClassStr.split(",");
//        int binary = 1;
//        for (int i = 0; i < classArr.length; i++){
//            ObjectClass objectClass = new ObjectClass();
//            objectClass.setClassName(classArr[i]);
//            objectClass.setKorClassName(korArr[i]);
//            objectClass.setBinaryInt(binary);
//            objectClassRepository.save(objectClass);
//            binary *= 2;
//        }
//    }


    public List<VideoDto> searchVideoListFinal(String content){
        String classStr = "person,bicycle,car,motorcycle,airplane,bus,train,truck,boat,bench,bird,cat,dog,horse,bear,umbrella,bottle,cup,pizza,donut,cake,chair,couch,tv,laptop,teddybear";
        String korStr="사람,자전거,자동차,오토바이,비행기,버스,기차,트럭,보트,벤치,새,고양이,개,말,곰,우산,병,컵,피자,도넛,케이크,의자,소파,tv,노트북,곰인형";
        String[] classArr = classStr.split(",");
        String[] korArr = korStr.split(",");

        // 검색어 띄어쓰기 제거 후 , 를 기준으로 분리
        content = content.replaceAll(" ","");
        String[] contents = content.split(",");
        String[] searchName = new String[contents.length];
        int[] searchNum = new int[contents.length];

        // 검색어 객체명과 객체수 분리
        for(int i = 0; i < contents.length; i++){
            searchName[i] = contents[i].replaceAll("[0-9]", "");
            String tmp = contents[i].replaceAll("[^0-9]","");
            searchNum[i] = tmp.isEmpty() ? 0 : Integer.parseInt(tmp);
        }

        //  java의 경우 2진수 변수가 없어서 int로 저장
        int bitNum = 0;
        //  탐지할 수 있는 객체와 인덱스를 1:1로 매칭하여 저장
        int[] searchArr = new int[26];

        //  검색어가 없는 경우 넘어가기
        if(!content.equals("")){
            for(int i = 0; i < searchName.length; i++){
                System.out.println(searchName[i]);
                int loc = -1;
                if(Arrays.asList(classArr).indexOf(searchName[i]) != -1){
                    //  객체별 할당된 인덱스 찾기
                    loc = Arrays.asList(classArr).indexOf(searchName[i]);
                }
                else if(Arrays.asList(korArr).indexOf(searchName[i]) != -1){
                    //  한글은 인덱스를 찾고 영어로 저장
                    loc = Arrays.asList(korArr).indexOf(searchName[i]);
                    searchName[i]= classArr[loc];
                }
                //  할당된 인덱스에 객체의 수 저장
                searchArr[loc] = searchNum[i];

                ObjectClass objectClass = objectClassService.getObject(searchName[i]);
                // db에서 객체에 할당된 이진수를 불러와 or연산
                bitNum = bitNum|objectClass.getBinaryInt();
            }
        }


        //  db에 query를 날리고 결과 영상들을 받아옴
        List<VideoDto> videoDtoList = new ArrayList<>();
        for(String urbanMapping : urbanResultRepository.searchFinal(bitNum, searchArr)){
            VideoMapping video = videoRepository.findByUrban(urbanMapping);
            VideoDto videoDto = new VideoDto();
            videoDto.setFileName(video.getFileName());
            videoDto.setId(video.getId());
            videoDto.setFileType(video.getFileType());
            videoDto.setPrivacyStatus(true);
            videoDtoList.add(videoDto);
        }

        return videoDtoList;
    }

    public JSONObject searchVideoTime(String id, String content){
        String classStr = "person,bicycle,car,motorcycle,airplane,bus,train,truck,boat,bench,bird,cat,dog,horse,bear,umbrella,bottle,cup,pizza,donut,cake,chair,couch,tv,laptop,teddy bear";
        String korStr="사람,자전거,자동차,오토바이,비행기,버스,기차,트럭,보트,벤치,새,고양이,개,말,곰,우산,병,컵,피자,도넛,케이크,의자,소파,tv,노트북,곰인형";
        String[] classArr = classStr.split(",");
        String[] korArr = korStr.split(",");
        // 검색어 띄어쓰기 제거 후 , 를 기준으로 분리
        content = content.replaceAll(" ","");
        String[] contents = content.split(",");

        // 검색어 객체명과 객체수 분리
        String[] searchName = new String[contents.length];
        int[] searchNum = new int[contents.length];
        for(int i = 0; i < contents.length; i++){
            searchName[i] = contents[i].replaceAll("[0-9]", "");//숫자 제거
            String tmp = contents[i].replaceAll("[^0-9]","");//문자 제거
            // 의자 1, 사람 과 같이 수가 없는 경우 0으로 세팅
            searchNum[i] = tmp.isEmpty() ? 0 : Integer.parseInt(tmp);
        }

        //  javadml
        int bitNum = 0;
        int[] searchArr = new int[26];
        for(int i = 0; i < searchName.length; i++){
            int loc = -1;
            if(Arrays.asList(classArr).indexOf(searchName[i]) != -1){
                loc = Arrays.asList(classArr).indexOf(searchName[i]);
            }
            else if(Arrays.asList(korArr).indexOf(searchName[i]) != -1){
                loc = Arrays.asList(korArr).indexOf(searchName[i]);
                searchName[i]= classArr[loc];
            }
            System.out.println(loc);
            searchArr[loc] = searchNum[i];

            ObjectClass objectClass = objectClassService.getObject(searchName[i]);
            bitNum = bitNum|objectClass.getBinaryInt();
        }

        String timeStr = urbanResultRepository.searchTime(id, bitNum, searchArr);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("timeStr", timeStr);

        return jsonObject;
    }

    public void temp(){
        for(UrbanResult urbanResult : urbanResultRepository.findAll()){
            Video video = urbanResult.getVideo();
            urbanResult.setVid(video.getId());
            urbanResultRepository.save(urbanResult);
        }
    }
    public Video getFile(String fileId) {
        return videoRepository.findById(fileId)
                .orElseThrow(() -> new VideoNotFoundException("File not found with id " + fileId));
    }
}
