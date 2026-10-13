package com.example.apppet.model;

public class PetType {
    public final long id;
    public final String name;

    public PetType(long id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
