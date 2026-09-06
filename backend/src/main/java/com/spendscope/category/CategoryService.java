package com.spendscope.category;

import com.spendscope.audit.AuditEventType;
import com.spendscope.audit.AuditService;
import com.spendscope.common.error.ConflictException;
import com.spendscope.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final AuditService auditService;

    public CategoryService(CategoryRepository categoryRepository, AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(Long userId) {
        return categoryRepository.findAccessible(userId).stream().map(CategoryResponse::from).toList();
    }

    /**
     * Resolves a category the user is allowed to reference.
     *
     * <p>Called from the transaction and budget services before storing a
     * {@code categoryId}, so a client cannot attach its data to another user's
     * private category.
     */
    @Transactional(readOnly = true)
    public Category require(Long userId, Long categoryId) {
        return categoryRepository.findAccessibleById(categoryId, userId)
                .orElseThrow(() -> NotFoundException.of("Category", categoryId));
    }

    @Transactional(readOnly = true)
    public Optional<Category> findBuiltInByName(String name) {
        return categoryRepository.findByUserIdIsNullAndNameIgnoreCase(name);
    }

    @Transactional
    public CategoryResponse create(Long userId, CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsAccessibleByName(name, userId)) {
            throw new ConflictException("A category named '" + name + "' already exists");
        }
        Category saved = categoryRepository.save(new Category(userId, name, request.type()));
        auditService.record(userId, AuditEventType.CATEGORY_CREATED, Map.of("categoryId", saved.getId()));
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse update(Long userId, Long categoryId, CategoryRequest request) {
        Category category = requireOwnCustom(userId, categoryId);
        String name = request.name().trim();
        if (!category.getName().equalsIgnoreCase(name)
                && categoryRepository.existsAccessibleByName(name, userId)) {
            throw new ConflictException("A category named '" + name + "' already exists");
        }
        category.setName(name);
        category.setType(request.type());
        return CategoryResponse.from(category);
    }

    /**
     * Deletes a custom category. Transactions that referenced it keep existing
     * with a null category (ON DELETE SET NULL) rather than disappearing.
     */
    @Transactional
    public void delete(Long userId, Long categoryId) {
        Category category = requireOwnCustom(userId, categoryId);
        categoryRepository.delete(category);
        auditService.record(userId, AuditEventType.CATEGORY_DELETED, Map.of("categoryId", categoryId));
    }

    /**
     * Built-in categories are shared by every user, so allowing one user to
     * rename or delete one would change the app for everybody.
     */
    private Category requireOwnCustom(Long userId, Long categoryId) {
        return categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "Category " + categoryId + " was not found among your custom categories"));
    }
}
