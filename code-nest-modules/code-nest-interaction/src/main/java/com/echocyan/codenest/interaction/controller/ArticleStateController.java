package com.echocyan.codenest.interaction.controller;

import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.interaction.service.ArticleLikeService;
import com.echocyan.codenest.interaction.service.FavoriteService;
import com.echocyan.codenest.interaction.vo.ArticleInteractionStateVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Tag(name = "点赞")
@RestController
@RequiredArgsConstructor
public class ArticleStateController {

    /**
     * 一次最多查询的文章数，与列表页的最大 size 一致。
     */
    private static final int MAX_IDS = 50;

    private final ArticleLikeService articleLikeService;
    private final FavoriteService favoriteService;

    @Operation(summary = "批量查询点赞、收藏状态", description = "以文章 ID 为 key；ids 最多 " + MAX_IDS + " 个，"
            + "不存在的文章按未点赞、未收藏返回")
    @GetMapping("/articles/states")
    public Result<Map<Long, ArticleInteractionStateVO>> states(@RequestParam @Size(max = MAX_IDS) List<Long> ids) {
        long userId = AuthContext.currentUserId();
        Set<Long> liked = articleLikeService.listLikedArticleIds(userId, ids);
        Set<Long> favorited = favoriteService.listFavoritedArticleIds(userId, ids);
        Map<Long, ArticleInteractionStateVO> states = new LinkedHashMap<>();
        ids.forEach(id -> states.put(id, new ArticleInteractionStateVO(liked.contains(id), favorited.contains(id))));
        return Result.ok(states);
    }
}
