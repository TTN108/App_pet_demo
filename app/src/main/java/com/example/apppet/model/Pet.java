package com.example.apppet.model;

public class Pet {
    public long id;
    public long userId;
    public long petTypeId;
    public Long breedId;
    public String name;
    public String gender;
    public String birthDate;
    public String color;
    public Double weight;
    public String description;
    public String createdAt;
    public String updatedAt;
    public String petTypeName;
    public String breedName;
    public String avatarUri;

    public Pet() {
    }

    public Pet(long id, long userId, String name, long petTypeId, String petTypeName, String breedName, String avatarUri) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.petTypeId = petTypeId;
        this.petTypeName = petTypeName;
        this.breedName = breedName;
        this.avatarUri = avatarUri;
    }

    // Helper getters for compatibility
    public int getId() { return (int) id; }
    public int getUserId() { return (int) userId; }
    public String getName() { return name != null ? name : ""; }
    public int getCategoryId() { return (int) petTypeId; }
    public String getCategoryName() { return petTypeName != null ? petTypeName : "Mèo"; }
    public String getBreed() { return breedName != null ? breedName : ""; }
    public String getImageUri() { return avatarUri != null ? avatarUri : ""; }
}