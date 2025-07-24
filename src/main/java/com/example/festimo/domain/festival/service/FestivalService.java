package com.example.festimo.domain.festival.service;

import com.example.festimo.domain.festival.document.FestivalDocument;
import com.example.festimo.domain.festival.domain.Festival;
import com.example.festimo.domain.festival.dto.FestivalDetailsTO;
import com.example.festimo.domain.festival.dto.FestivalTO;
import com.example.festimo.domain.festival.repository.FestivalRepository;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class FestivalService {

    @Value("${SEARCH_FESTIVAL_API_KEY}")
    private String SEARCH_FESTIVAL_API_KEY;
    @Value("${INFO_FESTIVAL_API_KEY}")
    private String INFO_FESTIVAL_API_KEY;

    @Autowired
    private FestivalRepository festivalRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    private final ModelMapper modelMapper = new ModelMapper();

    @Scheduled(cron = "0 0 0 * * ?")
    public void scheduleRefreshEvents() {
        refreshEvents();
    }

    @Transactional
    public void refreshEvents() {
        try {
            // 기존 데이터를 삭제하여 데이터 갱신
            festivalRepository.deleteAll();

            resetAutoIncrement();

            // API 호출로 데이터 가져오기
            List<FestivalTO> events = getAllEvents();

            // 가져온 데이터를 데이터베이스에 저장
            for (FestivalTO event : events) {
                insert(event);
            }
            syncFestivalData();
        } catch (Exception e) {
            System.out.println("refreshEvents 도중 에러 발생: " + e.getMessage());
        }
    }

    public List<FestivalTO> getAllEvents(){
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory("https://apis.data.go.kr/B551011/KorService1");
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);

        String baseUrl = "https://apis.data.go.kr/B551011/KorService1";
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setUriTemplateHandler(factory);

        List<FestivalTO> festivalList = new ArrayList<>();
        int pageNo = 1;
        int numOfRows = 100;
        try {
            while(true) {
                String url = factory.expand("/searchFestival1?serviceKey={serviceKey}&eventStartDate={eventStartDate}" +
                                "&pageNo={pageNo}&numOfRows={numOfRows}&MobileApp={MobileApp}" +
                                "&MobileOS={MobileOS}&listYN={listYN}&arrange={arrange}&_type={_type}",
                        Map.of(
                                "serviceKey", SEARCH_FESTIVAL_API_KEY,
                                "eventStartDate", "20230729",
                                "pageNo", pageNo,
                                "numOfRows", numOfRows,
                                "MobileApp", "AppTest",
                                "MobileOS", "ETC",
                                "listYN", "Y",
                                "arrange", "O",
                                "_type", "json"
                        )).toString();
                URI uri = new URI(url);
                ResponseEntity<Map> responseEntity = restTemplate.getForEntity(uri, Map.class);
                Map<String, Object> response = responseEntity.getBody();

                // 결과 확인
                // JSON 데이터에서 필요한 정보 추출
                Map<String, Object> body = (Map<String, Object>) ((Map<String, Object>) response.get("response")).get("body");
                int totalCount = (int) body.get("totalCount");
                Map<String, Object> items = (Map<String, Object>) body.get("items");

                // 데이터가 없으면 반복 종료
                if (items == null || !items.containsKey("item")) {
                    break;
                }

                List<Map<String, Object>> itemList = (List<Map<String, Object>>) items.get("item");

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
                for (Map<String, Object> item : itemList) {
                    FestivalTO festivalTO = new FestivalTO();
                    festivalTO.setTitle((String) item.get("title"));
                    festivalTO.setAddress((String) item.get("addr1") + " " + (String) item.get("addr2"));

                    String cat2 = (String) item.get("cat2");
                    String category;
                    if ("A0207".equals(cat2)) {
                        category = "축제";
                    } else if ("A0208".equals(cat2)) {
                        category = "행사";
                    } else {
                        category = "기타";
                    }

                    festivalTO.setCategory(category);
                    festivalTO.setStartDate(LocalDate.parse((CharSequence) item.get("eventstartdate"), formatter));
                    festivalTO.setEndDate(LocalDate.parse((CharSequence) item.get("eventenddate"), formatter));
                    festivalTO.setImage((String) item.get("firstimage"));
                    festivalTO.setXCoordinate(Float.parseFloat((String) item.get("mapx")));
                    festivalTO.setYCoordinate(Float.parseFloat((String) item.get("mapy")));
                    festivalTO.setPhone((String) item.get("tel"));

                    festivalTO.setContentId(Integer.parseInt((String) item.get("contentid")));

                    festivalList.add(festivalTO);
                }
                int totalPages = (int) Math.ceil((double) totalCount / numOfRows);
                if(pageNo >= totalPages){
                    break;
                }
                pageNo++;
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
        return festivalList;
    }

    private FestivalDetailsTO getFestivalDescription(int contentId) {
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory("https://apis.data.go.kr/B551011/KorService1");
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);

        String baseUrl = "https://apis.data.go.kr/B551011/KorService1";
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setUriTemplateHandler(factory);

        FestivalDetailsTO details = new FestivalDetailsTO();
        try{
            String url = factory.expand("/detailInfo1?serviceKey={serviceKey}&MobileApp={MobileApp}" +
                            "&MobileOS={MobileOS}&contentId={contentId}&contentTypeId={contentTypeId}&_type={_type}",
                    Map.of(
                            "serviceKey", INFO_FESTIVAL_API_KEY,
                            "MobileApp", "AppTest",
                            "MobileOS", "ETC",
                            "contentId", contentId,
                            "contentTypeId", "15",
                            "_type", "json"
                    )).toString();
            URI uri = new URI(url);
            ResponseEntity<Map> responseEntity = restTemplate.getForEntity(uri, Map.class);
            Map<String, Object> response = responseEntity.getBody();

            // 결과 확인
            Map<String, Object> body = (Map<String, Object>) ((Map<String, Object>) response.get("response")).get("body");
            Object items = body.get("items");

            if (items instanceof Map) {
                List<Map<String, Object>> itemList = (List<Map<String, Object>>) ((Map<String, Object>) items).get("item");
                if (itemList != null) {
                    for (Map<String, Object> item : itemList) {
                        String infoName = (String) item.get("infoname");
                        String infoText = (String) item.get("infotext");
                        details.getDetails().add(new FestivalDetailsTO.Detail(infoName, infoText));
                    }
                }
            } else if (items instanceof List) {
                List<Map<String, Object>> itemList = (List<Map<String, Object>>) items;
                for (Map<String, Object> item : itemList) {
                    String infoName = (String) item.get("infoname");
                    String infoText = (String) item.get("infotext");
                    details.getDetails().add(new FestivalDetailsTO.Detail(infoName, infoText));
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching description: " + e.getMessage());
        }
        return details;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetAutoIncrement() {
        entityManager.createNativeQuery("ALTER TABLE festival AUTO_INCREMENT = 1").executeUpdate();
    }

    @Transactional
    public void insert(FestivalTO to){
//        ModelMapper modelMapper = new ModelMapper();
        Festival festival = modelMapper.map(to, Festival.class);

        festivalRepository.save(festival);
    }


    public Page<FestivalTO> findPaginated(Pageable pageable) {
        Page<Festival> festivals = festivalRepository.findAll(pageable);
//        ModelMapper modelMapper = new ModelMapper();
        Page<FestivalTO> page = festivals.map(festival -> modelMapper.map(festival, FestivalTO.class));
        return page;
    }

public Page<FestivalTO> findPaginatedWithCache(Pageable pageable) {
    String cacheKey = "festivals:page:" + pageable.getPageNumber() + ":" + pageable.getPageSize();
    String totalElementsKey = "festivals:totalElements";

    // 1. 캐시된 페이지 데이터 확인
    Object cachedData = redisTemplate.opsForValue().get(cacheKey);
    if (cachedData != null) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

            // List<FestivalTO>로 캐시 된 데이터 Deserialize
            List<FestivalTO> cachedList = objectMapper.convertValue(cachedData, new TypeReference<List<FestivalTO>>() {});

            // 2. 캐시된 totalElements 확인
            String totalElementsStr = (String) redisTemplate.opsForValue().get(totalElementsKey);
            long totalElements;
            if (totalElementsStr != null) {
                try {
                    totalElements = Long.parseLong(totalElementsStr);
                } catch (NumberFormatException e) {
                    // 숫자 형식이 아니면 DB에서 조회
                    totalElements = festivalRepository.count();
                }
            } else {
                // 캐시에 값이 없으면 DB에서 조회
                totalElements = festivalRepository.count();
            }

            return new PageImpl<>(cachedList, pageable, totalElements);
        } catch (Exception e) {
            System.err.println("Failed to deserialize cached data: " + e.getMessage());
            redisTemplate.delete(cacheKey);
        }
    }

    // 3. 캐시가 없는 경우 DB에서 조회
    Page<FestivalTO> page = findPaginated(pageable);
    System.out.println("Retrieved page: " + page.getContent());

    // 4. 페이지 데이터와 전체 개수를 캐시에 저장
    redisTemplate.opsForValue().set(cacheKey, page.getContent(), Duration.ofHours(24));
    redisTemplate.opsForValue().set(totalElementsKey, String.valueOf(page.getTotalElements()), Duration.ofHours(24));


    return page;
}

    @Transactional(readOnly = true)
    public FestivalTO findById(int id){
        Festival festival = festivalRepository.findById(String.valueOf(id)).orElse(null);
        if (festival == null) {
            return null;
        }
//        ModelMapper modelMapper = new ModelMapper();
        FestivalTO to = modelMapper.map(festival, FestivalTO.class);

        int contentId = festival.getContentId();
        FestivalDetailsTO details = getFestivalDescription(contentId);

        to.setFestivalDetails(details);

        return to;
    }

    public Page<FestivalTO> search(String keyword, Pageable pageable) {
        Page<Festival> festivalPage = festivalRepository.findByTitleContainingIgnoreCase(keyword, pageable);

        Criteria criteria = new Criteria("title").contains(keyword).boost(2.0f)
                .or(new Criteria("description").matches(keyword).boost(1.0f))
                .or(new Criteria("location").matches(keyword).boost(1.0f));

        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(pageable);

        List<SearchHit<FestivalDocument>> searchHits = elasticsearchOperations.search(query, FestivalDocument.class).getSearchHits();

        List<FestivalTO> festivalTOList = searchHits.stream()
                .map(hit -> modelMapper.map(hit.getContent(), FestivalTO.class))
                .collect(Collectors.toList());

        long totalHits = elasticsearchOperations.count(query, FestivalDocument.class);

        return new PageImpl<>(festivalTOList, pageable, totalHits);
    }

    @Transactional
    public void syncFestivalData() {
        List<Festival> festivals = festivalRepository.findAll();

        if (!festivals.isEmpty()) {
            List<FestivalDocument> festivalDocuments = festivals.stream()
                    .map(festival -> modelMapper.map(festival, FestivalDocument.class))
                    .collect(Collectors.toList());

            elasticsearchOperations.save(festivalDocuments);
        }
    }

    public Page<FestivalTO> filterByMonth(int year, int month, Pageable pageable) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDayOfMonth = yearMonth.atDay(1);
        LocalDate lastDayOfMonth = yearMonth.atEndOfMonth();

//        ModelMapper modelMapper = new ModelMapper();
        Page<Festival> festivals = festivalRepository.findByMonth(firstDayOfMonth, lastDayOfMonth, pageable);
        Page<FestivalTO> page = festivals.map(festival -> new ModelMapper().map(festival, FestivalTO.class));

        return page;
    }

    public Page<FestivalTO> filterByRegion(String region, Pageable pageable) {
        Page<Festival> festivals = festivalRepository.findByRegion(region, pageable);

//        ModelMapper modelMapper = new ModelMapper();
        Page<FestivalTO> page = festivals.map(festival -> modelMapper.map(festival, FestivalTO.class));
        return page;
    }
}

