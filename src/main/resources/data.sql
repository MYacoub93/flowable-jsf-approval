-- Sample users for the approval workflow
-- Roles: INITIATOR, MANAGER, FINANCE

INSERT INTO users (username, full_name, department, role, email, active) VALUES
('alice',   'Alice Initiator',   'IT',       'INITIATOR', 'alice@example.com',   TRUE),
('bob',     'Bob Manager',       'IT',       'MANAGER',   'bob@example.com',     TRUE),
('carol',   'Carol Manager',     'Finance',  'MANAGER',   'carol@example.com',   TRUE),
('dave',    'Dave Finance',      'Finance',  'FINANCE',   'dave@example.com',    TRUE),
('eve',     'Eve Finance',       'Finance',  'FINANCE',   'eve@example.com',     TRUE),
('frank',   'Frank Initiator',   'HR',       'INITIATOR', 'frank@example.com',   TRUE),
('grace',   'Grace Manager',     'HR',       'MANAGER',   'grace@example.com',   TRUE);
