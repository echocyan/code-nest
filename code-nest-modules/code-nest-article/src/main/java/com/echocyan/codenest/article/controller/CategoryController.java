package com.echocyan.codenest.article.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.article.convert.CategoryConverter;
import com.echocyan.codenest.article.service.CategoryService;
import com.echocyan.codenest.article.vo.CategoryVO;
import com.echocyan.codenest.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SaIgnore
@Tag(name = "分类")
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryConverter categoryConverter;

    @Operation(summary = "全部分类", description = "按预置顺序返回")
    @GetMapping("/categories")
    public Result<List<CategoryVO>> list() {
        return Result.ok(categoryConverter.toVOs(categoryService.list()));
    }
}
