
# Search Engine

Разработка локального поискового движка, который представляет собой Spring-приложение (JAR-файл). Приложение работает с локально установленной базой данных MySQL, имеет простой веб-интерфейс и API для управления и получения результатов поисковых запросов.
## Стек технологий

- **Java** 17
- **Spring Boot** 
- **Spring Data JPA**
- **Jsoup** для парсинга HTML
- **MySQL** для хранения данных
- **Thymeleaf** для рендеринга веб-страниц

## Описание проекта

Проект включает:
- Индексацию веб-страниц с новостных сайтов.
- Хранение информации о страницах, леммах и индексах в базе данных.
- API для поиска по индексированным данным.
- Панель управления для мониторинга статистики индексации.

## Установка и запуск

1. **Клонируйте репозиторий**:

   ```bash
   git clone https://github.com/smithy13/SearchEngine-main.git

# Настройка базы данных:

- Используйте Docker для запуска MySQL контейнера:
  docker run --name search_engine -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=search_engine -e MYSQL_USER=username -e MYSQL_PASSWORD=password -p 3306:3306 -d mysql:8
* Приложение будет доступно по умолчанию на http://localhost:8080.


