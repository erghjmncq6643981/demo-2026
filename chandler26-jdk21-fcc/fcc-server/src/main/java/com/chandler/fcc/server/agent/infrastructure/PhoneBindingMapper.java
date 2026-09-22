package com.chandler.fcc.server.agent.infrastructure;

import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 话机拨号绑定持久化端口。
 */
@Mapper
public interface PhoneBindingMapper {

    /**
     * 校验 SIP 分机为启用资源。
     *
     * @param extension 已认证 SIP 分机
     * @return 绑定上下文，不匹配时为空
     */
    Map<String, Object> bindingContext(@Param("extension") String extension);

    /**
     * 锁定目标分机和坐席，串行化换绑。
     *
     * @param extension 已认证 SIP 分机
     * @param owner 坐席工号
     * @return 被锁定的分机和坐席标识，不存在时为空
     */
    Map<String, Object> lockBindingTarget(
        @Param("extension") String extension,
        @Param("owner") String owner
    );

    /**
     * 检查待换绑双方是否存在活动通话。
     *
     * @param owner 坐席工号
     * @param extension 分机号
     * @return 活动通话数量
     */
    int busy(@Param("owner") String owner, @Param("extension") String extension);

    /**
     * 失效坐席或话机的旧 SIP 绑定历史。
     *
     * @param owner 坐席工号
     * @param extension 分机号
     * @return 更新数量
     */
    int disableBindings(@Param("owner") String owner, @Param("extension") String extension);

    /**
     * 清除目标坐席原当前终端。
     *
     * @param owner 坐席工号
     * @return 更新数量
     */
    int deactivateCurrent(@Param("owner") String owner);

    /**
     * 清理旧分机归属。
     *
     * @param owner 坐席工号
     * @param extension 分机号
     * @return 更新数量
     */
    int clearExtensions(@Param("owner") String owner, @Param("extension") String extension);

    /**
     * 将分机绑定到坐席。
     *
     * @param owner 坐席工号
     * @param extension 分机号
     * @return 更新数量
     */
    int bindExtension(@Param("owner") String owner, @Param("extension") String extension);

    /**
     * 追加可审计的终端绑定历史。
     *
     * @param id 绑定记录标识
     * @param owner 坐席工号
     * @param extension 分机号
     * @return 插入数量
     */
    int appendBinding(
        @Param("id") long id,
        @Param("owner") String owner,
        @Param("extension") String extension
    );

    /**
     * 追加话机自助绑定形成的当前终端切换审计。
     *
     * @param id 审计主键
     * @param owner 坐席工号
     * @param oldBindingId 原当前绑定主键
     * @param newBindingId 新当前绑定主键
     * @return 插入数量
     */
    int appendSelectionAudit(
        @Param("id") long id,
        @Param("owner") String owner,
        @Param("oldBindingId") Long oldBindingId,
        @Param("newBindingId") long newBindingId
    );
}
