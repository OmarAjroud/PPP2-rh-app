package com.hrapp.rh.departement;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface DepartementMapper {

    @Mapping(target = "id", ignore = true)
    Departement toEntity(DepartementRequest request);

    @Mapping(target = "id", ignore = true)
    void updateEntity(DepartementRequest request, @MappingTarget Departement departement);
}
