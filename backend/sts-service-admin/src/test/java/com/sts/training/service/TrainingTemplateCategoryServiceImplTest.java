package com.sts.training.service;

import com.sts.training.dto.TrainingTemplateCategoryQuery;
import com.sts.training.dto.TrainingTemplateCategoryUpdateRequest;
import com.sts.training.entity.TrainingTemplateCategory;
import com.sts.training.mapper.TrainingTemplateCategoryMapper;
import com.sts.training.mapper.TrainingTemplateMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingTemplateCategoryServiceImplTest {

  @Test
  void tree_buildsStableChildrenFromAllVisibleCategories() {
    TrainingTemplateCategoryMapper mapper = mock(TrainingTemplateCategoryMapper.class);
    TrainingTemplateCategoryServiceImpl service = new TrainingTemplateCategoryServiceImpl(mapper,
                                                                                          mock(TrainingTemplateMapper.class));
    TrainingTemplateCategory root = category(1L, 0L, "根分类", 2);
    TrainingTemplateCategory child = category(2L, 1L, "子分类", 1);
    when(mapper.selectList(any())).thenReturn(List.of(root, child));

    var tree = service.tree(new TrainingTemplateCategoryQuery());

    assertThat(tree).hasSize(1);
    assertThat(tree.getFirst().getChildren()).extracting(item -> item.getTemplateCategoryId()).containsExactly(2L);
  }

  @Test
  void delete_rejectsCategoryWithChildrenWithoutDeletingIt() {
    TrainingTemplateCategoryMapper categoryMapper = mock(TrainingTemplateCategoryMapper.class);
    TrainingTemplateMapper templateMapper = mock(TrainingTemplateMapper.class);
    TrainingTemplateCategoryServiceImpl service = new TrainingTemplateCategoryServiceImpl(categoryMapper,
                                                                                          templateMapper);
    when(categoryMapper.selectById(1L)).thenReturn(category(1L, 0L, "根分类", 1));
    when(categoryMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.delete(1L))
      .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT)
      .hasFieldOrPropertyWithValue("code", 8039);

    verify(categoryMapper, never()).deleteById(anyLong());
    verifyNoInteractions(templateMapper);
  }

  @Test
  void update_versionConflict_throwsConflictWithoutSilentSuccess() {
    TrainingTemplateCategoryMapper categoryMapper = mock(TrainingTemplateCategoryMapper.class);
    TrainingTemplateCategoryServiceImpl service = new TrainingTemplateCategoryServiceImpl(categoryMapper,
                                                                                          mock(TrainingTemplateMapper.class));
    TrainingTemplateCategory existing = category(1L, 0L, "根分类", 1);
    existing.setVersion(2);
    when(categoryMapper.selectById(1L)).thenReturn(existing);
    when(categoryMapper.updateById(any(TrainingTemplateCategory.class))).thenReturn(0);
    TrainingTemplateCategoryUpdateRequest req = new TrainingTemplateCategoryUpdateRequest();
    req.setVersion(1);
    req.setCategoryName("根分类");
    req.setSortOrder(1);
    req.setStatus(1);

    assertThatThrownBy(() -> service.update(1L, req))
      .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT)
      .hasFieldOrPropertyWithValue("code", 8041);
  }

  private TrainingTemplateCategory category(Long id, Long parentId, String name, Integer sortOrder) {
    TrainingTemplateCategory category = new TrainingTemplateCategory();
    category.setTemplateCategoryId(id);
    category.setParentId(parentId);
    category.setCategoryName(name);
    category.setSortOrder(sortOrder);
    category.setStatus(1);
    return category;
  }
}
