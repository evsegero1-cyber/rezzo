# Требования к 3D-моделям Chevrolet Rezzo

Сейчас в приложении — **учебная заглушка** (`app/src/main/assets/models/rezzo_placeholder.glb`),
её генерирует `tools/gen_placeholder_car.py`. Габариты взяты из паспортных данных
(4350 × 1755 × 1580 мм, база 2600 мм, шины 185/70 R14), форма кузова и размещение агрегатов — условные.

Настоящую модель можно подключить **без изменения кода**:
- через приложение: «Мой Rezzo» → «3D-модель» → «Импортировать GLB / glTF»;
- или заменить файл в `assets/models` и поле `model` в `assets/scene/car_scene.json`.

## Формат

- glTF 2.0, лучше **GLB** (один файл). `.gltf` — только со встроенными (base64) буферами и текстурами.
- Только модели Chevrolet Rezzo / Daewoo Tacuma (U100) этого поколения, версия 1.6. Модели других машин не подходят.
- Единицы — **метры**. Оси: **Y вверх, X вперёд, Z влево** (сторона водителя, левый руль). Центр между осями, низ колёс на Y = 0.
  Если единицы другие, приложение впишет модель по длине 4,35 м, но точки на модели (hotspots) могут сместиться.
- Рекомендуемый объём для смартфона: до 300–500 тыс. треугольников, текстуры до 2048 px, PBR (metallic-roughness).

## Структура сцены

1. Каждая деталь — **отдельный узел** (node) с уникальным именем из таблицы ниже.
2. Подвижные части (двери, капот, дверь багажника) — узлы, у которых **начало координат совпадает с осью шарнира**.
   Двери открываются вращением вокруг Y на передней кромке, капот и дверь багажника — вокруг Z на петлях.
   Геометрия — в дочерних узлах (например, `door_front_l` → `door_front_l_panel`, `door_front_l_frame`, `window_front_l`).
3. Опускные стёкла — дочерние узлы дверей, опускаются смещением по −Y.
4. Кузовные панели и стёкла — материал с `alphaMode: BLEND` (для режима «Прозрачный кузов»). Если материалы непрозрачные,
   приложение в этом режиме просто скроет кузов.
5. Фары, поворотники, фонари — отдельные узлы (их подсвечивает приложение).
6. Агрегаты (двигатель, КПП, подвеска, тормоза, выпуск, бак) — отдельные узлы: нужны для подсветки, разборки и выбора касанием.
7. Каждому узлу в `car_scene.json` сопоставлен `partId` из базы знаний (`core/src/main/assets/kb/parts/*.json`).

Новые детали, анимации и шаги разборки добавляются правкой `car_scene.json`, новые описания — правкой JSON базы знаний.

## Подвижные узлы и свет

| Свет (id) | Узлы |
|---|---|
| `parking` | `headlight_l`, `headlight_r`, `taillight_l`, `taillight_r` |
| `headlights` | `headlight_l`, `headlight_r`, `taillight_l`, `taillight_r` |
| `turn_left` | `turn_front_l`, `turn_rear_l` |
| `turn_right` | `turn_front_r`, `turn_rear_r` |
| `brake` | `taillight_l`, `taillight_r` |
| `reverse` | `reverse_light_l`, `reverse_light_r` |

## Узлы деталей

| Узел | partId | Сторона / уточнение | Движение |
|---|---|---|---|
| `door_front_l` | `door_front` | левая | подвижный: rotate, ось [0, 1, 0], угол 65° |
| `door_rear_l` | `door_rear` | левая | подвижный: rotate, ось [0, 1, 0], угол 65° |
| `window_front_l` | `side_window` | передняя левая | подвижный: translate, смещение [0, -0.4, 0] |
| `window_rear_l` | `side_window` | задняя левая | подвижный: translate, смещение [0, -0.32, 0] |
| `headlight_l` | `headlight` | левая |  |
| `turn_front_l` | `turn_signal` | передний левый |  |
| `turn_rear_l` | `turn_signal` | задний левый |  |
| `taillight_l` | `taillight` | левый |  |
| `reverse_light_l` | `reverse_light` | левый |  |
| `strut_front_l` | `strut_front` | левая |  |
| `spring_front_l` | `coil_spring` | передняя левая |  |
| `lower_arm_front_l` | `lower_arm` | левый |  |
| `tie_rod_l` | `tie_rod` | левая |  |
| `driveshaft_l` | `driveshaft` | левая |  |
| `shock_rear_l` | `shock_rear` | левый |  |
| `spring_rear_l` | `coil_spring` | задняя левая |  |
| `links_rear_l` | `rear_suspension` | левая |  |
| `brake_disc_fl` | `brake_disc` | левый |  |
| `caliper_fl` | `brake_caliper` | левый |  |
| `brake_drum_rl` | `brake_drum` | левый |  |
| `wheel_fl` | `wheel` | переднее левое |  |
| `wheel_rl` | `wheel` | заднее левое |  |
| `seat_front_l` | `seats` | водителя |  |
| `door_front_r` | `door_front` | правая | подвижный: rotate, ось [0, 1, 0], угол -65° |
| `door_rear_r` | `door_rear` | правая | подвижный: rotate, ось [0, 1, 0], угол -65° |
| `window_front_r` | `side_window` | передняя правая | подвижный: translate, смещение [0, -0.4, 0] |
| `window_rear_r` | `side_window` | задняя правая | подвижный: translate, смещение [0, -0.32, 0] |
| `headlight_r` | `headlight` | правая |  |
| `turn_front_r` | `turn_signal` | передний правый |  |
| `turn_rear_r` | `turn_signal` | задний правый |  |
| `taillight_r` | `taillight` | правый |  |
| `reverse_light_r` | `reverse_light` | правый |  |
| `strut_front_r` | `strut_front` | правая |  |
| `spring_front_r` | `coil_spring` | передняя правая |  |
| `lower_arm_front_r` | `lower_arm` | правый |  |
| `tie_rod_r` | `tie_rod` | правая |  |
| `driveshaft_r` | `driveshaft` | правая |  |
| `shock_rear_r` | `shock_rear` | правый |  |
| `spring_rear_r` | `coil_spring` | задняя правая |  |
| `links_rear_r` | `rear_suspension` | правая |  |
| `brake_disc_fr` | `brake_disc` | правый |  |
| `caliper_fr` | `brake_caliper` | правый |  |
| `brake_drum_rr` | `brake_drum` | правый |  |
| `wheel_fr` | `wheel` | переднее правое |  |
| `wheel_rr` | `wheel` | заднее правое |  |
| `seat_front_r` | `seats` | пассажира |  |
| `fender_front_l` | `body_shell` |  |  |
| `pillar_a_l` | `body_shell` |  |  |
| `pillar_b_l` | `body_shell` |  |  |
| `pillar_c_l` | `body_shell` |  |  |
| `quarter_rear_l` | `body_shell` |  |  |
| `pillar_d_l` | `body_shell` |  |  |
| `roof_rail_l` | `body_shell` |  |  |
| `sill_l` | `body_shell` |  |  |
| `fender_front_r` | `body_shell` |  |  |
| `pillar_a_r` | `body_shell` |  |  |
| `pillar_b_r` | `body_shell` |  |  |
| `pillar_c_r` | `body_shell` |  |  |
| `quarter_rear_r` | `body_shell` |  |  |
| `pillar_d_r` | `body_shell` |  |  |
| `roof_rail_r` | `body_shell` |  |  |
| `sill_r` | `body_shell` |  |  |
| `roof` | `body_shell` |  |  |
| `cowl` | `body_shell` |  |  |
| `rear_panel` | `body_shell` |  |  |
| `front_panel` | `body_shell` |  |  |
| `bumper_front` | `bumper` | передний |  |
| `bumper_rear` | `bumper` | задний |  |
| `glass_quarter_front_l` | `fixed_glass` |  |  |
| `glass_quarter_front_r` | `fixed_glass` |  |  |
| `glass_quarter_rear_l` | `fixed_glass` |  |  |
| `glass_quarter_rear_r` | `fixed_glass` |  |  |
| `tailgate_glass` | `fixed_glass` |  |  |
| `hood` | `hood` |  | подвижный: rotate, ось [0, 0, 1], угол 52° |
| `tailgate` | `tailgate` |  | подвижный: rotate, ось [0, 0, 1], угол -82° |
| `windshield` | `windshield` |  |  |
| `engine_block` | `engine_block` |  |  |
| `cylinder_head` | `cylinder_head` |  |  |
| `valve_cover` | `valve_cover` |  |  |
| `ignition_coil` | `ignition_coil` |  |  |
| `spark_plugs` | `spark_plugs` |  |  |
| `intake_manifold` | `intake_manifold` |  |  |
| `throttle_body` | `throttle_body` |  |  |
| `fuel_rail` | `fuel_rail` |  |  |
| `injectors` | `injectors` |  |  |
| `exhaust_manifold` | `exhaust_manifold` |  |  |
| `alternator` | `alternator` |  |  |
| `accessory_belt` | `accessory_belt` |  |  |
| `water_pump` | `water_pump` |  |  |
| `starter` | `starter` |  |  |
| `gearbox` | `gearbox` |  |  |
| `air_filter` | `air_filter` |  |  |
| `battery` | `battery` |  |  |
| `fuse_box` | `fuse_box` |  |  |
| `brake_booster` | `brake_booster` |  |  |
| `master_cylinder` | `master_cylinder` |  |  |
| `radiator` | `radiator` |  |  |
| `cooling_fan` | `cooling_fan` |  |  |
| `coolant_reservoir` | `coolant_reservoir` |  |  |
| `coolant_hoses` | `coolant_hoses` |  |  |
| `thermostat` | `thermostat` |  |  |
| `catalytic_converter` | `catalytic_converter` |  |  |
| `exhaust_pipe` | `exhaust_pipe` |  |  |
| `muffler` | `muffler` |  |  |
| `fuel_tank` | `fuel_tank` |  |  |
| `fuel_pump` | `fuel_pump` |  |  |
| `fuel_line` | `fuel_line` |  |  |
| `steering_rack` | `steering_rack` |  |  |
| `dashboard` | `dashboard` |  |  |
| `instrument_cluster` | `instrument_cluster` |  |  |
| `steering_wheel` | `steering_wheel` |  |  |
| `gear_lever` | `gear_lever` |  |  |
| `pedals` | `pedals` |  |  |
| `floor` | `body_shell` | пол |  |
| `firewall` | `body_shell` | моторный щит |  |
| `oil_pan` | `oil_system` | масляный картер |  |
| `timing_cover` | `timing_drive` | кожух ГРМ |  |
| `crank_pulley` | `crankshaft` | шкив коленвала |  |
| `clutch_housing` | `clutch` | картер сцепления |  |
| `air_duct` | `air_filter` | воздуховод |  |
| `exhaust_downpipe` | `exhaust_pipe` | приёмная труба |  |
| `tailpipe` | `exhaust_pipe` | выпускная труба |  |
| `stabilizer_front` | `stabilizer` | передний |  |
| `stabilizer_rear` | `stabilizer` | задний |  |
| `seat_rear` | `seats` | задний диван |  |
