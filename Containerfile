# IBM Semeru Runtimesの最新のJDKイメージを使用
FROM ibm-semeru-runtimes:open-21-jdk

# 作業ディレクトリを設定
WORKDIR /app

# ローカルのJavaファイルをコンテナにコピー
COPY DiaryRestServer.java .

# コンテナ起動時にJavaアプリケーションを実行
CMD ["java", "DiaryRestServer.java", "/data/diary.txt"]
