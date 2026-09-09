-- Local-only data for validating pagination, filtering and search.
-- Demo credentials: pagination_demo / Demo12345!

-- Local-only data for validating pagination, filtering and search.
-- Demo credentials: pagination_demo / Demo12345!

INSERT INTO users (
    username,
    display_name,
    password,
    email,
    role,
    created_at,
    is_deactivated
)
VALUES (
    'pagination_demo',
    'Pagination Demo',
    '$2a$10$.C04uYvjROUuXZ8hXEFcBOyQJcbAntzua1ffgtNti30PxlVnQ1SqW',
    'pagination_demo@example.test',
    'USER',
    CURRENT_TIMESTAMP,
    FALSE
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO users (
    username, display_name, password, email, role, created_at, is_deactivated
)
SELECT
    'page_user_' || LPAD(series::text, 2, '0'),
    'Page User ' || LPAD(series::text, 2, '0'),
    '$2a$10$.C04uYvjROUuXZ8hXEFcBOyQJcbAntzua1ffgtNti30PxlVnQ1SqW',
    'page_user_' || LPAD(series::text, 2, '0') || '@example.test',
    'USER',
    CURRENT_TIMESTAMP - (series || ' days')::interval,
    series % 9 = 0
FROM generate_series(1, 25) AS series
ON CONFLICT (username) DO NOTHING;

INSERT INTO projects (
    name, description, start_date, end_date, user_id, created_at
)
SELECT
    'Pagination Project ' || LPAD(series::text, 2, '0'),
    'Local demo project used to verify list navigation.',
    CURRENT_DATE - (series * 2),
    CURRENT_DATE + (30 - series),
    demo.id,
    CURRENT_TIMESTAMP - (series || ' days')::interval
FROM generate_series(1, 12) AS series
CROSS JOIN (SELECT id FROM users WHERE username = 'pagination_demo') AS demo
WHERE NOT EXISTS (
    SELECT 1
    FROM projects existing
    WHERE existing.user_id = demo.id
      AND existing.name = 'Pagination Project ' || LPAD(series::text, 2, '0')
);

INSERT INTO project_members (project_id, user_id, role, joined_at)
SELECT project.id, project.user_id, 'OWNER', project.created_at
FROM projects project
JOIN users owner_user ON owner_user.id = project.user_id
WHERE owner_user.username = 'pagination_demo'
ON CONFLICT (project_id, user_id) DO NOTHING;

INSERT INTO project_members (project_id, user_id, role, joined_at)
SELECT
    project.id,
    member_user.id,
    CASE WHEN member_series.series % 4 = 0 THEN 'VIEWER' ELSE 'MEMBER' END,
    CURRENT_TIMESTAMP - (member_series.series || ' hours')::interval
FROM (SELECT id FROM projects WHERE name = 'Pagination Project 01' ORDER BY id LIMIT 1) project
CROSS JOIN generate_series(1, 25) AS member_series(series)
JOIN users member_user
  ON member_user.username = 'page_user_' || LPAD(member_series.series::text, 2, '0')
ON CONFLICT (project_id, user_id) DO NOTHING;

INSERT INTO tasks (
    title, description, status, priority, user_id, project_id, created_at, due_date
)
SELECT
    'Pagination Task ' || LPAD(series::text, 2, '0'),
    CASE
        WHEN series % 3 = 0 THEN 'Prepare report and review pagination search behaviour.'
        ELSE 'Local task fixture for paging and filter verification.'
    END,
    (ARRAY['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'CHANGES_REQUESTED', 'DONE'])[((series - 1) % 5) + 1],
    (ARRAY['LOW', 'MEDIUM', 'HIGH', 'URGENT'])[((series - 1) % 4) + 1],
    demo.id,
    (
        SELECT project.id
        FROM projects project
        WHERE project.user_id = demo.id
          AND project.name LIKE 'Pagination Project %'
        ORDER BY project.name
        OFFSET ((series - 1) % 12)
        LIMIT 1
    ),
    CURRENT_TIMESTAMP - (series || ' hours')::interval,
    CURRENT_DATE + (series - 20)
FROM generate_series(1, 48) AS series
CROSS JOIN (SELECT id FROM users WHERE username = 'pagination_demo') AS demo
WHERE NOT EXISTS (
    SELECT 1
    FROM tasks existing
    WHERE existing.user_id = demo.id
      AND existing.title = 'Pagination Task ' || LPAD(series::text, 2, '0')
);

INSERT INTO expenses (
    description, amount, user_id, task_id, category, created_at, expense_date
)
SELECT
    'Pagination Expense ' || LPAD(series::text, 2, '0'),
    25000 + (series * 7500),
    demo.id,
    CASE WHEN series % 5 = 0 THEN NULL ELSE task.id END,
    (ARRAY['FOOD', 'TRANSPORTATION', 'LEARNING', 'HOBBIES', 'OTHERS'])[((series - 1) % 5) + 1],
    CURRENT_TIMESTAMP - (series || ' hours')::interval,
    CURRENT_DATE - (series % 30)
FROM generate_series(1, 45) AS series
CROSS JOIN (SELECT id FROM users WHERE username = 'pagination_demo') AS demo
LEFT JOIN LATERAL (
    SELECT candidate.id
    FROM tasks candidate
    WHERE candidate.user_id = demo.id
      AND candidate.title LIKE 'Pagination Task %'
    ORDER BY candidate.title
    OFFSET ((series - 1) % 48)
    LIMIT 1
) task ON TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM expenses existing
    WHERE existing.user_id = demo.id
      AND existing.description = 'Pagination Expense ' || LPAD(series::text, 2, '0')
);
