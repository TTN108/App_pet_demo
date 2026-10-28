#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Huấn luyện model nhận diện giống thú cưng (MobileNetV2 transfer learning) từ ảnh đã tải bằng
download_pet_images.py, rồi xuất TensorFlow Lite cho chức năng "Chụp ảnh" trong app.

Cài đặt (máy có Python 3.10-3.12):   pip install tensorflow pillow
Chạy:
    python train_breed_classifier.py --pet-dir pet --assets ../app/src/main/assets/ml
Kết quả trong --assets:
    breed_classifier.tflite   input float32 [1,224,224,3], giá trị RGB 0..255 (đã gắn sẵn bước chuẩn hoá bên trong model)
                              output float32 [1,N] xác suất (softmax)
    labels.txt                mỗi dòng = breed_slug, đúng thứ tự output (slug cũng là một alias trong bảng breed_aliases)
    model_report.txt          độ chính xác top-1/top-3 và theo từng giống - ĐỌC trước khi tin kết quả.

Lưu ý thực tế: model chỉ biết các giống trong CATALOG; ảnh không phải thú cưng vẫn bị gán vào một giống nào đó.
App dùng ngưỡng tin cậy (CONFIDENCE_THRESHOLD) và luôn cho người dùng chọn lại giống thủ công.
"""
import argparse
import csv
import json
import random
import sys
from collections import Counter, defaultdict
from pathlib import Path

for _s in (sys.stdout, sys.stderr):
    if hasattr(_s, "reconfigure"):
        _s.reconfigure(encoding="utf-8")

IMG = 224


def load_items(pet_dir):
    """Trả về ([(đường_dẫn_ảnh, breed_slug)], {đường_dẫn: 'train'|'val'|'test'}).
    Cột split trong manifest.csv (do export_training_set.py ghi) cho phép chia tập CỐ ĐỊNH giữa các lần huấn luyện."""
    items, split_map, manifest = [], {}, pet_dir / "manifest.csv"
    if manifest.exists():
        with manifest.open(newline="", encoding="utf-8") as f:
            for r in csv.DictReader(f):
                p = pet_dir / r["file"]
                if p.exists():
                    items.append((str(p), r["breed_slug"]))
                    if r.get("split") in ("train", "val", "test"):
                        split_map[str(p)] = r["split"]
    else:
        for p in pet_dir.glob("*/*.jpg"):
            items.append((str(p), p.parent.name))
        for p in pet_dir.glob("*/*/*.jpg"):
            items.append((str(p), p.parent.name))
    return items, split_map


def split_items(items, split_map, val_ratio, seed):
    """Trả về (train, val, test). Có cột split đầy đủ thì dùng nguyên; không thì chia ngẫu nhiên và KHÔNG có test."""
    if split_map and all(p in split_map for p, _ in items):
        return ([i for i in items if split_map[i[0]] == "train"],
                [i for i in items if split_map[i[0]] == "val"],
                [i for i in items if split_map[i[0]] == "test"])
    train, val = split(items, val_ratio, seed)
    return train, val, []


def split(items, val_ratio, seed):
    rnd = random.Random(seed)
    by = defaultdict(list)
    for it in items:
        by[it[1]].append(it)
    train, val = [], []
    for slug, lst in by.items():
        rnd.shuffle(lst)
        k = max(1, int(len(lst) * val_ratio))
        val += lst[:k]
        train += lst[k:]
    rnd.shuffle(train)
    return train, val


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pet-dir", default="pet")
    ap.add_argument("--assets", default="../app/src/main/assets/ml")
    ap.add_argument("--epochs-head", type=int, default=8)
    ap.add_argument("--epochs-ft", type=int, default=8)
    ap.add_argument("--batch", type=int, default=32)
    ap.add_argument("--min-per-class", type=int, default=15, help="bỏ giống có ít ảnh hơn mức này")
    args = ap.parse_args()

    import tensorflow as tf  # import muộn để --help chạy được khi chưa cài TF

    items, split_map = load_items(Path(args.pet_dir))
    counts = Counter(s for _, s in items)
    keep = {s for s, n in counts.items() if n >= args.min_per_class}
    dropped = sorted(set(counts) - keep)
    if dropped:
        print("Bỏ giống quá ít ảnh (< %d): %s" % (args.min_per_class, ", ".join(dropped)))
    items = [it for it in items if it[1] in keep]
    labels = sorted(keep)
    if len(labels) < 2:
        sys.exit("Cần ít nhất 2 giống có đủ ảnh. Chạy download_pet_images.py trước.")
    idx = {s: i for i, s in enumerate(labels)}
    train, val, test = split_items(items, split_map, 0.2, 42)
    print("Số giống: %d | train: %d | val: %d | test: %d" % (len(labels), len(train), len(val), len(test)))
    if not test:
        print("CẢNH BÁO: không có tập test riêng (manifest chưa có cột split) - độ chính xác bên dưới là trên tập val, hơi lạc quan.")

    def make_ds(rows, training):
        paths = tf.constant([p for p, _ in rows])
        ys = tf.constant([idx[s] for _, s in rows], dtype=tf.int32)
        ds = tf.data.Dataset.from_tensor_slices((paths, ys))
        if training:
            ds = ds.shuffle(len(rows), seed=1)

        def load(path, y):
            img = tf.io.decode_image(tf.io.read_file(path), channels=3, expand_animations=False)
            img = tf.image.resize(img, (IMG, IMG))
            return tf.cast(img, tf.float32), y
        ds = ds.map(load, num_parallel_calls=tf.data.AUTOTUNE)
        if training:
            aug = tf.keras.Sequential([
                tf.keras.layers.RandomFlip("horizontal"),
                tf.keras.layers.RandomRotation(0.08),
                tf.keras.layers.RandomZoom(0.15),
                tf.keras.layers.RandomContrast(0.15),
            ])
            ds = ds.batch(args.batch).map(lambda x, y: (aug(x, training=True), y),
                                          num_parallel_calls=tf.data.AUTOTUNE)
        else:
            ds = ds.batch(args.batch)
        return ds.prefetch(tf.data.AUTOTUNE)

    train_ds, val_ds = make_ds(train, True), make_ds(val, False)
    test_ds = make_ds(test, False) if test else None

    base = tf.keras.applications.MobileNetV2(input_shape=(IMG, IMG, 3), include_top=False, weights="imagenet")
    base.trainable = False
    inp = tf.keras.Input(shape=(IMG, IMG, 3), dtype="float32")              # RGB 0..255
    x = tf.keras.layers.Rescaling(1.0 / 127.5, offset=-1.0)(inp)            # -> [-1, 1] như MobileNetV2 yêu cầu
    x = base(x, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(0.25)(x)
    out = tf.keras.layers.Dense(len(labels), activation="softmax")(x)
    model = tf.keras.Model(inp, out)

    n_tr = Counter(idx[s] for _, s in train)
    class_weight = {i: len(train) / (len(labels) * n_tr[i]) for i in range(len(labels))}
    metrics = ["accuracy", tf.keras.metrics.SparseTopKCategoricalAccuracy(k=min(3, len(labels)), name="top3")]
    cb = [tf.keras.callbacks.EarlyStopping(monitor="val_accuracy", patience=3, restore_best_weights=True)]

    model.compile(tf.keras.optimizers.Adam(1e-3), "sparse_categorical_crossentropy", metrics=metrics)
    model.fit(train_ds, validation_data=val_ds, epochs=args.epochs_head, class_weight=class_weight, callbacks=cb)

    base.trainable = True
    for layer in base.layers[:-40]:
        layer.trainable = False
    model.compile(tf.keras.optimizers.Adam(1e-5), "sparse_categorical_crossentropy", metrics=metrics)
    model.fit(train_ds, validation_data=val_ds, epochs=args.epochs_ft, class_weight=class_weight, callbacks=cb)

    # ---- đánh giá theo từng giống
    correct, total = Counter(), Counter()
    for xb, yb in val_ds:
        pred = tf.argmax(model(xb, training=False), axis=1).numpy()
        for p, y in zip(pred, yb.numpy()):
            total[y] += 1
            correct[y] += int(p == y)
    _, acc, top3 = model.evaluate(val_ds, verbose=0)
    report = ["Top-1: %.3f | Top-3: %.3f | giống: %d | ảnh val: %d" % (acc, top3, len(labels), len(val)), ""]
    for i, s in enumerate(labels):
        report.append("%-20s %d/%d  (%.0f%%)" % (s, correct[i], total[i], 100.0 * correct[i] / max(1, total[i])))

    if test_ds is not None:
        _, t_acc, t_top3 = model.evaluate(test_ds, verbose=0)
        report.insert(1, "TEST (chưa từng dùng để chọn model): Top-1 %.3f | Top-3 %.3f | ảnh test: %d" % (t_acc, t_top3, len(test)))

    # ---- xuất TFLite
    conv = tf.lite.TFLiteConverter.from_keras_model(model)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite = conv.convert()
    out_dir = Path(args.assets)
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "breed_classifier.tflite").write_bytes(tflite)
    (out_dir / "labels.txt").write_text("\n".join(labels) + "\n", encoding="utf-8")
    (out_dir / "model_report.txt").write_text("\n".join(report) + "\n", encoding="utf-8")

    # ---- kiểm tra file .tflite chạy được và khớp model gốc trên vài ảnh val
    interp = tf.lite.Interpreter(model_content=tflite)
    interp.allocate_tensors()
    i_in, i_out = interp.get_input_details()[0], interp.get_output_details()[0]
    ok = 0
    sample = val[:min(40, len(val))]
    for path, slug in sample:
        img = tf.image.resize(tf.io.decode_image(tf.io.read_file(path), channels=3, expand_animations=False), (IMG, IMG))
        interp.set_tensor(i_in["index"], tf.expand_dims(tf.cast(img, tf.float32), 0).numpy())
        interp.invoke()
        ok += int(labels[int(interp.get_tensor(i_out["index"]).argmax())] == slug)
    report.append("")
    report.append("Kiểm tra .tflite trên %d ảnh val: %d đúng" % (len(sample), ok))
    (out_dir / "model_report.txt").write_text("\n".join(report) + "\n", encoding="utf-8")
    print("\n".join(report))
    print("\nĐã ghi model vào %s (%.1f MB)" % (out_dir, len(tflite) / 1e6))


if __name__ == "__main__":
    main()
