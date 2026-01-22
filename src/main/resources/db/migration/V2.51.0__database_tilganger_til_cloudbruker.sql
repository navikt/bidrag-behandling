-- Grant permissions on all existing sequences
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO cloudsqliamuser;

-- Grant permissions on all existing tables
GRANT ALL ON ALL TABLES IN SCHEMA public TO cloudsqliamuser;

-- Set default privileges for future objects (keep your existing commands)
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO cloudsqliamuser;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO cloudsqliamuser;
