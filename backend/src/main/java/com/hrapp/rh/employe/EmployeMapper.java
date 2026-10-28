package com.hrapp.rh.employe;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface EmployeMapper {

    @Mapping(source = "departement.id",  target = "departementId")
    @Mapping(source = "departement.nom", target = "departementNom")
    @Mapping(source = "role.id",         target = "roleId")
    @Mapping(source = "role.nom",        target = "roleNom")
    EmployeResponse toResponse(Employe employe);

    @Mapping(source = "departement.id",  target = "departementId")
    @Mapping(source = "departement.nom", target = "departementNom")
    @Mapping(source = "role.id",         target = "roleId")
    @Mapping(source = "role.nom",        target = "roleNom")
    @Mapping(source = "manager.id",      target = "managerId")
    @Mapping(target = "managerNomComplet",
            expression = "java(employe.getManager() == null ? null : employe.getManager().getPrenom() + \" \" + employe.getManager().getNom())")
    EmployeDetailResponse toDetail(Employe employe);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "departement", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "manager", ignore = true)
    Employe toEntity(EmployeRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "departement", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "manager", ignore = true)
    void updateEntity(EmployeRequest request, @MappingTarget Employe employe);
}
