package com.spendscope.category;

public record CategoryResponse(Long id, String name, CategoryType type, boolean builtIn) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getType(),
                category.isBuiltIn());
    }
}
