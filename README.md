# Appdenoticias

Aplicación Android desarrollada en Java para mostrar noticias deportivas recientes desde distintas fuentes.

## Funciones principales

- Mostrar noticias deportivas.
- Mostrar imagen, título y contenido de cada noticia.
- Actualizar las noticias.
- Filtrar noticias recientes.
- Consultar diferentes fuentes deportivas.

## Tecnologías utilizadas

- Android Studio
- Java
- XML
- Gradle
- Docker
- Git
- GitHub
- Docker Hub

## Docker

El proyecto utiliza Docker para crear un entorno aislado que contiene las herramientas necesarias para compilar la aplicación Android.

La imagen utiliza Android SDK 36.

Para construir la imagen:

```bash
docker build -t appdenoticias .
