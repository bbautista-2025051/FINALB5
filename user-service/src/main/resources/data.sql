INSERT INTO usuarios (nombre, telefono, email, password, rol) VALUES
('Administrador Clinica', '3001112222', 'admin@veterinaria.com', '$2a$10$0ZJJPnbtNsATTKMg0U9py.J6mlSRbchiddHiBfxCRJBPqGZ6HXrTq', 'ADMIN'),
('Veterinario Demo', '3003334444', 'veterinario@veterinaria.com', '$2a$10$a6pHvC4pkZIXkZA5K4CNQu0h8gsO5xeJLSwMnLeqW5uNNylO2bvCi', 'VET'),
('Cliente Demo', '3005556666', 'cliente@veterinaria.com', '$2a$10$AmB5bFbCAe68GoE0Nxs.cerX.PpiBz7rZPI2.EoY6mHKfWVIHsm3i', 'CLIENTE')
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre);
