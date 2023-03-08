package com.hmdp.mapper;

import com.hmdp.entity.BlogComments;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

//再对应的mapper上面实现基本的接口 BaseMapper
public interface BlogCommentsMapper extends BaseMapper<BlogComments> {
    //所有的CRUD都已经完成
    //不需要像以前一样配置一大堆文件：pojo-dao（连接mybatis，配置mapper.xml文件）==>service-controller
}
