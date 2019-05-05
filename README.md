# SibintekTest
### Задание №1
https://github.com/AlexLyr/SibintekTest/blob/master/Задание%20для%20Java%20разработчика.docx

Так как мне долго не отвечали как должны выглядеть сервисы, а в задании было сказано использовать только native java,
то я сделал на сокетах, один класс сервера, и два одинаковых класса ClientA и ClientB, чтобы было удобно из IDEA запускать

### Схема проекта Задание №1
[![Alt text](https://github.com/AlexLyr/SibintekTest/blob/master/scheme.png)](https://github.com/AlexLyr/SibintekTest/blob/master/scheme.png)


### Задание №2 (SQL)
https://github.com/AlexLyr/SibintekTest/blob/master/Задание%20по%20БД.docx


#### Задача 1
```sql
create index on cities (name);
delete
from cities a
where a.id NOT IN (
  select min(c.id)
  from cities c
  group by c.name
);
```

#### Задача 2
```sql
create index country on population (country);
SELECT country,city,cast(100 * citizen / partitioned.total as NUMERIC(5, 2))
FROM (
       SELECT rank() OVER (PARTITION BY country ORDER BY citizen DESC) "limit",
              sum(citizen) OVER (PARTITION BY country)                 "total",
              t.*
       FROM population t) partitioned
WHERE partitioned.limit <= 3 ORDER BY country ASC;
```