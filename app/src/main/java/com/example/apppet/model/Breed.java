package com.example.apppet.model;

public class Breed {
    public final long id; // id = 0 đại diện cho chưa rõ/không chọn giống
    public final long petTypeId;
    public final String name;
    public final String nameVi;

    public Breed(long id, long petTypeId, String name, String nameVi) {
        this.id = id;
        this.petTypeId = petTypeId;
        this.name = name;
        this.nameVi = nameVi;
    }

    @Override
    public String toString() {
        if (id == 0) return name;
        if (nameVi != null && !nameVi.trim().isEmpty() && !nameVi.equalsIgnoreCase(name)) {
            return nameVi + " (" + name + ")";
        }
        return name;
    }
}
