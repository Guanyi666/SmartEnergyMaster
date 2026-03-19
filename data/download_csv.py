from ucimlrepo import fetch_ucirepo

print("正在拉取原始数据...")
# 获取数据集
steel_data = fetch_ucirepo(id=851)

# 将特征数据 (Features) 导出为当前目录下的 CSV 文件
steel_data.data.features.to_csv("raw_steel_data.csv", index=False)

print("✅ 下载完成！请在当前目录下查看 raw_steel_data.csv 文件。")