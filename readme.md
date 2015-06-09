# Script CI API

Java 8 server to manage script saving & running

## Directory structure

```
root/
|-- jobs/
    |-- job1/
        |-- config.json
        |-- scripts/
            |-- script1.sh
            |-- script1e.sh
            |-- script2.sh
        |-- work/
            |-- [source files]
        |-- runs/
            |-- 1/
                |-- out.log
            |-- 2/
                |-- out.log
    |-- job2/
```