import os
import argparse
import torch
from ultralytics import YOLO
from datetime import datetime


def train_yolo(data_path, log_dirs, total_epochs=100, model_weights="yolov8n.pt"):
    model = YOLO(model_weights)

    # 设置日志文件路径
    log_dir = os.path.join('runs', 'log', log_dirs)
    os.makedirs(log_dir, exist_ok=True)
    log_file_path = os.path.join(log_dir, 'training_log.txt')

    print(f"开始训练: {data_path}")

    with open(log_file_path, 'a', encoding='utf-8') as log_file:
        now=datetime.now()
        log_file.write(f"\n\n\n\n------------------------------{now:%Y.%m.%d-%H:%M}------------------------------\n")
        log_file.write(f"Training started for {data_path}...\n")

        device = 0 if torch.cuda.is_available() else 'cpu'  # 关键修改
        print(f"使用设备: {device}")

        try:
            # 根据 显存, 数据集大小等因素调整训练参数
            print("开始训练...")
            model.train(
                data=data_path,
                imgsz=640,
                epochs=total_epochs,
                batch=-1,  # 自动调整批量大小
                close_mosaic=10,
                workers=2,
                device=device,  # 这里传入修改后的设备参数
                optimizer='SGD',
                project='runs/train',
                name=log_dirs,
                patience=30,     # 验证集不提升则提前停止
                degrees=15,      # 旋转
                shear=5,         # 剪切
                flipud=0.5,      # 上下翻转
                mosaic=1.0,      # 马赛克增强
                mixup=0.1,       # Mixup 增强
                # lr0=0.01, # 初始学习率，根据需要调整
            )
            log_file.write(f"训练成功完成 for {data_path}.\n")
        except Exception as e:
            safe_error_message = str(e).encode('utf-8', errors='replace').decode('utf-8')
            log_file.write(f"训练过程中出现错误 for {data_path}: {safe_error_message}\n")
        finally:
            log_file.write(f"训练过程结束 for {data_path}...\n")


def batch_train(data_yaml_paths, model_weights="yolov8n.pt", total_epochs=100):
    for data_path in data_yaml_paths:
        dataset_dir = os.path.dirname(data_path)
        dataset_name = os.path.basename(dataset_dir)
        log_dirs = os.path.join(dataset_name)
        train_yolo(data_path, log_dirs, total_epochs=total_epochs, model_weights=model_weights)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="YOLO 多数据集训练脚本")
    parser.add_argument("--epochs", type=int, default=100, help="训练轮数")
    parser.add_argument("--model-weights", type=str, help="模型权重文件")
    args = parser.parse_args()

    data_yaml_paths = [
        "./Dataset/data.yaml"
    ]

    batch_train(data_yaml_paths, model_weights=args.model_weights, total_epochs=args.epochs)