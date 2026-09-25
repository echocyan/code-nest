package com.echocyan.codenest.article.service;

import com.echocyan.codenest.article.vo.ArticleItemVO;
import com.echocyan.codenest.common.result.PageResult;

/**
 * 热榜：定时按热度公式重算最近 7 天发布的文章，取前 {@value #CAPACITY} 名存入 Redis，读取时分页。
 */
public interface HotArticleService {

    /** 榜单容量。 */
    int CAPACITY = 100;

    /** 每页条数。 */
    int PAGE_SIZE = 20;

    /** 最大页码，{@code CAPACITY / PAGE_SIZE}。 */
    int MAX_PAGE = 5;

    /**
     * 重算一次榜单并整体替换。多实例间互斥，其他实例正在重算时直接跳过。
     */
    void refresh();

    /**
     * 按热度倒序读取一页，每页 {@value #PAGE_SIZE} 条；已删除的文章被滤掉，因此一页可能不足 {@value #PAGE_SIZE} 条。
     *
     * @param page 从 1 开始，不超过 {@value #MAX_PAGE}
     * @return total 为榜单上的文章数，含读取时被滤掉的
     */
    PageResult<ArticleItemVO> page(int page);
}
