"""启动入口。"""

import logging

from app import create_app

logger = logging.getLogger(__name__)
app = create_app()

if __name__ == "__main__":
    logger.info("MindShelf 服务端启动 http://0.0.0.0:5000")
    app.run(host="0.0.0.0", port=5000, debug=True)
