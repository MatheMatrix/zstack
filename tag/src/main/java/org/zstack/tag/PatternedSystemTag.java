package org.zstack.tag;

import org.zstack.core.Platform;
import org.zstack.core.db.Q;
import org.zstack.core.db.SimpleQuery;
import org.zstack.core.db.SimpleQuery.Op;
import org.zstack.header.tag.SystemTagInventory;
import org.zstack.header.tag.SystemTagVO;
import org.zstack.header.tag.SystemTagVO_;
import org.zstack.utils.TagUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.zstack.utils.StringDSL.s;

/**
 */
public class PatternedSystemTag extends SystemTag {
    private static final CLogger logger = Utils.getLogger(SystemTag.class);

    public SensitiveTag annotation;

    public PatternedSystemTag(String tagFormat, Class resourceClass) {
        super(tagFormat, resourceClass);
    }

    @Override
    protected String useTagFormat() {
        return TagUtils.tagPatternToSqlPattern(tagFormat);
    }

    @Override
    protected Op useOp() {
        return Op.LIKE;
    }

    @Override
    public boolean isMatch(String tag) {
        return TagUtils.isMatch(tagFormat, tag);
    }

    @Override
    public void delete(String resourceUuid, Class resourceClass) {
        tagMgr.deleteSystemTagUseLike(useTagFormat(), resourceUuid, resourceClass.getSimpleName(), false);
    }

    public void delete(String resourceUuid, String tagFormat) {
        tagMgr.deleteSystemTagUseLike(tagFormat, resourceUuid, resourceClass.getSimpleName(), false);
    }

    @Override
    public void delete(String resourceUuid) {
        tagMgr.deleteSystemTagUseLike(useTagFormat(), resourceUuid, resourceClass.getSimpleName(), false);
    }

    @Override
    public void deleteInherentTag(String resourceUuid) {
        tagMgr.deleteSystemTagUseLike(useTagFormat(), resourceUuid, resourceClass.getSimpleName(), true);
    }

    public void deleteInherentTag(String resourceUuid, String tagFormat) {
        tagMgr.deleteSystemTagUseLike(tagFormat, resourceUuid, resourceClass.getSimpleName(), true);
    }

    @Override
    public void deleteInherentTag(String resourceUuid, Class resourceClass) {
        tagMgr.deleteSystemTagUseLike(useTagFormat(), resourceUuid, resourceClass.getSimpleName(), true);
    }

    public Map<String, String> getTokensByTag(String tag) {
        return TagUtils.parseIfMatch(tagFormat, tag);
    }

    public String getTokenByTag(String tag, String tokenName) {
        Map<String, String> tokens = getTokensByTag(tag);
        if (tokens == null) {
            return null;
        }
        return tokens.get(tokenName);
    }

    public Map<String, String> getTokensByResourceUuid(String resourceUuid, Class resourceClass) {
        String tag = getTag(resourceUuid, resourceClass);
        if (tag == null) {
            return null;
        }

        return TagUtils.parseIfMatch(tagFormat, tag);
    }

    public List<Map<String, String>> getTokensOfTagsByResourceUuid(String resourceUuid) {
        return getTokensOfTagsByResourceUuid(resourceUuid, resourceClass);
    }

    public List<Map<String, String>> getTokensOfTagsByResourceUuid(String resourceUuid, Class resourceClass) {
        List<Map<String, String>> res = new ArrayList<>();

        List<String> tags = getTags(resourceUuid, resourceClass);
        for (String tag : tags) {
            res.add(TagUtils.parseIfMatch(tagFormat, tag));
        }

        return res;
    }

    public Map<String, String> getTokensByResourceUuid(String resourceUuid) {
        return getTokensByResourceUuid(resourceUuid, resourceClass);
    }

    public String getTokenByResourceUuid(String resourceUuid, Class resourceClass, String tokenName) {
        Map<String, String> tokens = getTokensByResourceUuid(resourceUuid, resourceClass);
        if (tokens == null) {
            return null;
        }
        return tokens.get(tokenName);
    }

    public String getTokenByResourceUuid(String resourceUuid, String tokenName) {
        return getTokenByResourceUuid(resourceUuid, resourceClass, tokenName);
    }

    public String hideSensitiveInfo(String tag) {
        Class<? extends SensitiveTagOutputHandler> clz = this.annotation.customizeOutput();
        String result = tag;
        try {
            SensitiveTagOutputHandler sensitiveOutputHandler = clz.newInstance();
            result = sensitiveOutputHandler.desensitizeTag(this, tag);
        } catch (InstantiationException | IllegalAccessException e) {
            logger.warn("exception happened :", e);
        }
        return result;
    }

    public String instantiateTag(Map tokens) {
        return s(tagFormat).formatByMap(tokens);
    }

    public SystemTagInventory getTagInventory(String resourceUuid) {
        SimpleQuery<SystemTagVO> q = dbf.createQuery(SystemTagVO.class);
        q.add(SystemTagVO_.resourceUuid, Op.EQ, resourceUuid);
        q.add(SystemTagVO_.resourceType, Op.EQ, getResourceClass().getSimpleName());
        q.add(SystemTagVO_.tag, Op.LIKE, useTagFormat());
        SystemTagVO vo = q.find();
        return  vo == null ? null : SystemTagInventory.valueOf(vo);
    }

    public List<SystemTagInventory> getTagInventories(List<String> resourceUuids) {
        SimpleQuery<SystemTagVO> q = dbf.createQuery(SystemTagVO.class);
        q.add(SystemTagVO_.resourceUuid, Op.IN, resourceUuids);
        q.add(SystemTagVO_.resourceType, Op.EQ, getResourceClass().getSimpleName());
        q.add(SystemTagVO_.tag, Op.LIKE, useTagFormat());
        return SystemTagInventory.valueOf(q.list());
    }

    public List<SystemTagInventory> getTagInventories(String resourceUuid) {
        return SystemTagInventory.valueOf(Q.New(SystemTagVO.class).eq(SystemTagVO_.resourceType, getResourceClass().getSimpleName()).
                eq(SystemTagVO_.resourceUuid, resourceUuid).like(SystemTagVO_.tag, useTagFormat()).list());
    }

    public void copyTagInventories(String srcUuid, Class srcResourceClass, String dstUuid, Class dstResourceClass, boolean inherent) {
        if (getTag(srcUuid, srcResourceClass) == null) {
            return;
        }

        if (getTokenByResourceUuid(dstUuid, dstResourceClass, tagFormat) != null) {
            delete(dstUuid, tagFormat);
        }
        SystemTagVO svo = new SystemTagVO();
        svo.setUuid(Platform.getUuid());
        svo.setInherent(inherent);
        svo.setResourceUuid(dstUuid);
        svo.setResourceType(dstResourceClass.getSimpleName());
        svo.setTag(getTag(srcUuid, srcResourceClass));
        dbf.persistAndRefresh(svo);
    }

    public boolean updateTagByToken(String resourceUuid, String tokenName, String newTag) {
        SimpleQuery<SystemTagVO> q = dbf.createQuery(SystemTagVO.class);
        q.add(SystemTagVO_.resourceUuid, Op.EQ, resourceUuid);
        q.add(SystemTagVO_.resourceType, Op.EQ, getResourceClass().getSimpleName());
        q.add(SystemTagVO_.tag, Op.LIKE, useTagFormat());
        SystemTagVO vo = q.find();

        String oldTag = getTokenByResourceUuid(resourceUuid, tokenName);
        if (vo == null || oldTag == null) {
            return false;
        }

        vo.setTag(vo.getTag().replace(oldTag, newTag));
        dbf.updateAndRefresh(vo);

        return true;
    }
}
