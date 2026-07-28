-- Demo credentials for evaluation: both accounts use the password "password".
-- Hashes are BCrypt cost 10, generated with Spring Security's BCryptPasswordEncoder.
INSERT INTO app_user (id, email, password_hash, role) VALUES
  ('11111111-1111-4111-8111-111111111111', 'analyst@bookero.local',
   '$2a$10$a/thuH4xryHb58SNo4SAyeRJpaRH/4QBVl6FkmLJKyBhUd.0r7HxO', 'ANALYST'),
  ('22222222-2222-4222-8222-222222222222', 'traveler@bookero.local',
   '$2a$10$f/QQ2C9/PCZ4BzBL/2npE.i7l3GC1EK09DvSsqelGGg0lt5XrKQ8G', 'TRAVELER')
ON CONFLICT (email) DO NOTHING;
