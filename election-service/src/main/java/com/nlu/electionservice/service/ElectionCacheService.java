package com.nlu.electionservice.service;

import com.nlu.electionservice.entity.Election;
import com.nlu.electionservice.repository.ElectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ElectionCacheService {

    @Autowired
    private ElectionRepository electionRepository;

    @Cacheable(value = "election", key = "#id")
    public Optional<Election> findById(Long id) {
        log.debug(">>> [Cache MISS] election:{}", id);
        return electionRepository.findById(id);
    }

    @Cacheable(value = "elections-all")
    public List<Election> findAll() {
        log.debug(">>> [Cache MISS] elections-all");
        return electionRepository.findAll();
    }

    @CacheEvict(value = {"election", "elections-all"}, allEntries = true)
    public void evictAll() {
        log.info(">>> [Cache EVICT] Đã xóa cache elections");
    }

    @CacheEvict(value = "election", key = "#id")
    public void evictById(Long id) {
        log.info(">>> [Cache EVICT] election:{}", id);
    }
}
