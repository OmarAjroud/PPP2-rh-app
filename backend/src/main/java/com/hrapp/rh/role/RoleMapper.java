package com.hrapp.rh.role;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")   // devient un bean Spring injectable
public interface RoleMapper {

    RoleResponse versReponse(Role role);

    @Mapping(target = "id", ignore = true)
    Role versEntite(RoleRequest requete);

    @Mapping(target = "id", ignore = true)
    void mettreAJour(RoleRequest requete, @MappingTarget Role role);
}