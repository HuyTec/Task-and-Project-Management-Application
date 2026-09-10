package com.taskmanagement.repository.specification;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import com.taskmanagement.dto.task.TaskFilter;
import com.taskmanagement.dto.task.TaskWorkspaceView;
import com.taskmanagement.model.AssignmentStatus;
import com.taskmanagement.model.ProjectMember;
import com.taskmanagement.model.Task;
import com.taskmanagement.model.TaskAssignment;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> forUser(
            Long userId,
            TaskFilter filter
    ) {
        return forWorkspace(userId, TaskWorkspaceView.MY_WORK, filter);
    }

    public static Specification<Task> forWorkspace(
            Long userId,
            TaskWorkspaceView workspace,
            TaskFilter filter
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("User id is required for a user-scoped task query");
        }
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = commonPredicates(root, criteriaBuilder, filter);
            Predicate personalTask = criteriaBuilder.and(
                    criteriaBuilder.isNull(root.get("project")),
                    criteriaBuilder.equal(root.get("user").get("id"), userId)
            );

            TaskWorkspaceView selectedWorkspace = workspace == null ? TaskWorkspaceView.MY_WORK : workspace;
            if (selectedWorkspace == TaskWorkspaceView.REVIEW_QUEUE) {
                predicates.add(hasReviewAccess(root, query, criteriaBuilder, userId));
                predicates.add(criteriaBuilder.equal(root.get("status"), com.taskmanagement.model.TaskStatus.IN_REVIEW));
            } else if (selectedWorkspace == TaskWorkspaceView.ALL_ACCESSIBLE) {
                predicates.add(criteriaBuilder.or(
                        personalTask,
                        hasProjectMembership(root, query, criteriaBuilder, userId)
                ));
            } else {
                predicates.add(criteriaBuilder.or(
                        personalTask,
                        hasActiveAssignment(root, query, criteriaBuilder, userId)
                ));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate hasActiveAssignment(
            Root<Task> task,
            CriteriaQuery<?> query,
            CriteriaBuilder criteriaBuilder,
            Long userId
    ) {
        Subquery<Long> assignmentQuery = query.subquery(Long.class);
        Root<TaskAssignment> assignment = assignmentQuery.from(TaskAssignment.class);
        assignmentQuery.select(assignment.get("id")).where(
                criteriaBuilder.equal(assignment.get("task").get("id"), task.get("id")),
                criteriaBuilder.equal(assignment.get("assignee").get("user").get("id"), userId),
                criteriaBuilder.equal(assignment.get("status"), AssignmentStatus.ACTIVE)
        );
        return criteriaBuilder.exists(assignmentQuery);
    }

    private static Predicate hasProjectMembership(
            Root<Task> task,
            CriteriaQuery<?> query,
            CriteriaBuilder criteriaBuilder,
            Long userId
    ) {
        Subquery<Long> membershipQuery = query.subquery(Long.class);
        Root<ProjectMember> membership = membershipQuery.from(ProjectMember.class);
        membershipQuery.select(membership.get("id")).where(
                criteriaBuilder.equal(membership.get("project").get("id"), task.get("project").get("id")),
                criteriaBuilder.equal(membership.get("user").get("id"), userId)
        );
        return criteriaBuilder.exists(membershipQuery);
    }

    private static Predicate hasReviewAccess(
            Root<Task> task,
            CriteriaQuery<?> query,
            CriteriaBuilder criteriaBuilder,
            Long userId
    ) {
        return criteriaBuilder.equal(task.get("reviewer").get("user").get("id"), userId);
    }

    public static Specification<Task> all(TaskFilter filter) {
        return build(null, filter);
    }

    private static Specification<Task> build(Long userId, TaskFilter filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = commonPredicates(root, criteriaBuilder, filter);

            if (userId != null) {
                predicates.add(criteriaBuilder.equal(root.get("user").get("id"), userId));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static List<Predicate> commonPredicates(
            Root<Task> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            TaskFilter filter
    ) {
        List<Predicate> predicates = new ArrayList<>();
        if (filter == null) {
            return predicates;
        }

        if (StringUtils.hasText(filter.search())) {
            String keyword = "%" + escapeLike(filter.search().trim().toLowerCase()) + "%";

            predicates.add(
                    criteriaBuilder.or(
                            criteriaBuilder.like(
                                    criteriaBuilder.lower(root.get("title")),
                                    keyword,
                                    '\\'
                            ),
                            criteriaBuilder.like(
                                    criteriaBuilder.lower(root.get("description")),
                                    keyword,
                                    '\\'
                            )
                    )
            );
        }

        if (filter.status() != null) {
            predicates.add(criteriaBuilder.equal(root.get("status"), filter.status()));
        }

        if (filter.priority() != null) {
            predicates.add(criteriaBuilder.equal(root.get("priority"), filter.priority()));
        }

        if (filter.projectId() != null) {
            predicates.add(criteriaBuilder.equal(
                    root.get("project").get("id"),
                    filter.projectId()
            ));
        }

        if (filter.dueFrom() != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.get("dueDate"),
                    filter.dueFrom()
            ));
        }

        if (filter.dueTo() != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(
                    root.get("dueDate"),
                    filter.dueTo()
            ));
        }

        return predicates;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
