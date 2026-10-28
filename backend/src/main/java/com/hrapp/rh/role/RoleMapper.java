package com.hrapp.rh.role;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")   // devient un bean Spring injectable
public interface RoleMapper {

    RoleResponse toResponse(Role role);

    @Mapping(target = "id", ignore = true)
    Role toEntity(RoleRequest request);

    @Mapping(target = "id", ignore = true)
    void updateEntity(RoleRequest request, @MappingTarget Role role);
}
