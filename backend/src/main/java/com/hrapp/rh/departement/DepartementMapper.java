package com.hrapp.rh.departement;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface DepartementMapper {

    @Mapping(target = "id", ignore = true)
    Departement versEntite(DepartementRequest requete);

    @Mapping(target = "id", ignore = true)
    void mettreAJour(DepartementRequest requete, @MappingTarget Departement departement);
}