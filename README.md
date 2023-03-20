# What is this?

A simple Java app that runs a basic counter on many threads.
It measures how far each thread gets up the count to evaluate "progress"
and then does simple statistics on the results.

# Why?

The idea is to measure context switching when you have more threads than physical or logical processors.
Measuring diminishing returns, disparities between threads, etc.

# How can I run it?

You'll need a JDK (Java) and Maven. I used JDK 11.

```
git clone [mine] [yours]
mvn clean package exec:java
```

The application will generate a `data.csv` file that you can import into Excel, Google Sheets, etc. to create charts do other analysis.