package com.hrapp.rh.departement;

import com.hrapp.rh.employe.EmployeResume;

import java.util.List;

public record DepartementDetailResponse(Long id, String nom, String description,
                                        List<EmployeResume> employes) {}