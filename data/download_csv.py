import logging

from ucimlrepo import fetch_ucirepo

logging.basicConfig(level=logging.INFO, format="%(message)s")
logger = logging.getLogger("download_csv")

logger.info("Fetching UCI steel industry energy dataset...")
steel_data = fetch_ucirepo(id=851)

steel_data.data.features.to_csv("raw_steel_data.csv", index=False)

logger.info("Download complete. Saved raw_steel_data.csv in the current directory.")
