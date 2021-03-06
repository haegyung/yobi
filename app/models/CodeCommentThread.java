/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package models;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

import java.io.IOException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

import static models.CodeRange.Side;
import static models.CodeRange.Side.*;

/**
 * 커밋 뷰에서 생성되는 모든 쓰레드는 CodeCommentThread.
 *
 * @author Keesun Baik
 */
@Entity
@DiscriminatorValue("ranged")
public class CodeCommentThread extends CommentThread {
    private static final long serialVersionUID = 1L;

    public static final Finder<Long, CodeCommentThread> find = new Finder<>(Long.class, CodeCommentThread.class);

    @Embedded
    @Nullable
    public CodeRange codeRange = new CodeRange();

    public String prevCommitId = StringUtils.EMPTY;
    public String commitId;

    @Transient
    private Boolean _isOutdated;

    @ManyToMany(cascade = CascadeType.ALL)
    public List<User> codeAuthors = new ArrayList<>();

    public boolean isCommitComment() {
        return ObjectUtils.equals(prevCommitId, StringUtils.EMPTY);
    }

    private String unexpectedSideMessage(Side side) {
        return String.format("Expected '%s' or '%s', but '%s'", A, B, side);
    }

    public boolean isOnChangesOfPullRequest() {
        return isOnPullRequest() && StringUtils.isNotEmpty(commitId);
    }

    public boolean isOnAllChangesOfPullRequest() {
        return isOnChangesOfPullRequest() && StringUtils.isNotEmpty(prevCommitId);
    }

    public boolean isOutdated() throws IOException, GitAPIException {
        if (codeRange.startLine == null || prevCommitId == null || commitId == null) {
            return false;
        }

        // cache
        if (_isOutdated != null) {
            return _isOutdated;
        }

        if (pullRequest.mergedCommitIdFrom == null || pullRequest.mergedCommitIdTo == null) {
            return false;
        }

        String path = codeRange.path;
        if (path.length() > 0 && path.charAt(0) == '/') {
            path = path.substring(1);
        }

        Repository mergedRepository = pullRequest.getMergedRepository();

        switch(codeRange.startSide) {
            case A:
                _isOutdated = !PullRequest.noChangesBetween(mergedRepository,
                    pullRequest.mergedCommitIdFrom, mergedRepository, prevCommitId, path);
                break;
            case B:
                _isOutdated = !PullRequest.noChangesBetween(mergedRepository,
                    pullRequest.mergedCommitIdTo, mergedRepository, commitId, path);
                break;
            default:
                throw new RuntimeException(unexpectedSideMessage(codeRange.startSide));
        }

        if (_isOutdated) {
            return _isOutdated;
        }

        switch(codeRange.endSide) {
            case A:
                _isOutdated = !PullRequest.noChangesBetween(mergedRepository,
                    pullRequest.mergedCommitIdFrom, mergedRepository, prevCommitId, path);
                break;
            case B:
                _isOutdated = !PullRequest.noChangesBetween(mergedRepository,
                    pullRequest.mergedCommitIdTo, mergedRepository, commitId, path);
                break;
            default:
                throw new RuntimeException(unexpectedSideMessage(codeRange.endSide));
        }

        return _isOutdated;
    }
}
