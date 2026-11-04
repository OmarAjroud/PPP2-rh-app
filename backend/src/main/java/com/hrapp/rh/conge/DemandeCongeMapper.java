package com.hrapp.rh.conge;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DemandeCongeMapper {

    @Mapping(source = "employe.id", target = "employeId")
    @Mapping(target = "employeNomComplet",
            expression = "java(demande.getEmploye().getPrenom() + \" \" + demande.getEmploye().getNom())")
    @Mapping(target = "employeDepartementNom",
            expression = "java(demande.getEmploye().getDepartement() == null ? null : demande.getEmploye().getDepartement().getNom())")
    @Mapping(source = "typeConge.id", target = "typeCongeId")
    @Mapping(source = "typeConge.code", target = "typeCongeCode")
    @Mapping(source = "typeConge.libelle", target = "typeCongeLibelle")
    @Mapping(target = "decideParNomComplet",
            expression = "java(demande.getDecidePar() == null ? null : demande.getDecidePar().getPrenom() + \" \" + demande.getDecidePar().getNom())")
    DemandeCongeResponse toResponse(DemandeConge demande);
}