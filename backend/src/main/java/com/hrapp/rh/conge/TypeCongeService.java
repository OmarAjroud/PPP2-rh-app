package com.hrapp.rh.conge;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TypeCongeService {

    private final TypeCongeRepository repository;

    @Transactional(readOnly = true)
    public List<TypeCongeResponse> findAll() {
        return repository.findAllByOrderByLibelle().stream()
                .map(t -> new TypeCongeResponse(t.getId(), t.getCode(), t.getLibelle(),
                        t.isDeduitDuSolde(), t.getJoursParAn(), t.isRetroactifAutorise()))
                .toList();
    }
}