# PowerM3108

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)]()

**Power M3-108** — Android-приложение «Калькулятор мощности» для прибора М3-108.
Рассчитывает среднюю и импульсную мощность, ведёт таблицу результатов и
экспортирует её в Excel-совместимый файл.

## Возможности v2.0

- 🖥️ **Красное неоновое табло** прибора в стиле HUD.
- ⚡ **Расчёт мощности**: `P_ср = коэффициент × показание`, `P_имп = P_ср × Q`.
- 🎛️ **Скважность Q**: ручной ввод, грубая (±0,1) и точная (±0,01) регулировка,
  кнопка-пресет `Q` (переключение `16 ⇄ 10`).
- 📊 **Таблица 6 строк** с колонками `№ Л | Коэфф. | М3-108 | P средн. | P имп.`
- 🔢 **Переключение нумерации** строк: `1–6 ⇄ 7–12` (тап по «№ Л»).
- 🗂️ **Несколько таблиц** («КИУ № N»): добавление, переименование, удаление,
  выбор из выпадающего списка.
- 📤 **Экспорт в Excel**-совместимый HTML + отправка через системное меню.
- 🔋 Автосохранение всех данных в `SharedPreferences`.
- 🧪 Юнит-тесты расчёта (`PowerCalculatorTest`).

## Скриншот

![Скриншот главного экрана](docs/screenshots/main-screen.png)

## Сборка

Требуется [Android Studio](https://developer.android.com/studio) и JDK 17.
Gradle Wrapper включён в проект.

```bash
# GitVerse
git clone https://gitverse.ru/zoolbazuzu/Power_M3-108.git

# GitHub (зеркало)
git clone https://github.com/zool81-coder/Power_M3_108.git

cd PowerM3108

# Windows (PowerShell)
./gradlew.bat assembleDebug

# Linux/macOS
./gradlew assembleDebug
