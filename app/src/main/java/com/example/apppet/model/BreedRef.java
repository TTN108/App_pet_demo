package com.example.apppet.model;

/** Giống đã được map từ nhãn của model nhận diện (qua bảng breed_aliases). */
public class BreedRef {
    public final long breedId;
    public final long petTypeId;
    public final String breedName;
    public final String breedNameVi;
    public final String petTypeName;

    public BreedRef(long breedId, long petTypeId, String breedName, String breedNameVi, String petTypeName) {
        this.breedId = breedId;
        this.petTypeId = petTypeId;
        this.breedName = breedName;
        this.breedNameVi = breedNameVi;
        this.petTypeName = petTypeName;
    }

    public String displayName() {
        if (breedNameVi != null && !breedNameVi.trim().isEmpty() && !breedNameVi.equalsIgnoreCase(breedName)) {
            return breedNameVi + " (" + breedName + ")";
        }
        return breedName;
    }
}
