-- Inicialización de MySQL para la clínica veterinaria.
-- Los esquemas también se declaran aquí aunque Hibernate (ddl-auto=update)
-- crea las tablas al arrancar cada servicio.
-- NOTA: el usuario se crea con MYSQL_USER/MYSQL_PASSWORD en docker-compose;
-- este script asume que DB_USERNAME=vet (valor por defecto).

CREATE DATABASE IF NOT EXISTS vet_usuarios
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS vet_mascotas
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS vet_citas
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS vet_expedientes
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON vet_usuarios.* TO 'vet'@'%';
GRANT ALL PRIVILEGES ON vet_mascotas.* TO 'vet'@'%';
GRANT ALL PRIVILEGES ON vet_citas.* TO 'vet'@'%';
GRANT ALL PRIVILEGES ON vet_expedientes.* TO 'vet'@'%';

FLUSH PRIVILEGES;
