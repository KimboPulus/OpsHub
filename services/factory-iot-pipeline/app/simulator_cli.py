import argparse
import time

import requests


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--api", default="http://localhost:8000")
    parser.add_argument("--ticks", type=int, default=20)
    parser.add_argument("--sleep", type=float, default=0.5)
    args = parser.parse_args()

    for tick in range(1, args.ticks + 1):
        response = requests.post(f"{args.api}/simulator/tick", params={"count": 1}, timeout=10)
        response.raise_for_status()
        print(f"tick {tick}: {response.json()['created']} readings")
        time.sleep(args.sleep)


if __name__ == "__main__":
    main()
