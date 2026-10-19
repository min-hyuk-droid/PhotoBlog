# 예시 사진 출처

모두 위키미디어 공용(Wikimedia Commons)의 **퍼블릭 도메인 / CC0** 사진이다.
라이선스는 내려받을 때 Commons API 의 `LicenseShortName` 으로 확인했다. 출처 표기 의무는 없지만 기록해 둔다.

| 파일 | 라이선스 | 작가 | 원본 |
|---|---|---|---|
| `corgi_standing.jpg` | CC0 | Huoadg5888 | [Fawn and white Welsh Corgi puppy standing on rear legs and sticking out the tongue.jpg](https://commons.wikimedia.org/wiki/File:Fawn_and_white_Welsh_Corgi_puppy_standing_on_rear_legs_and_sticking_out_the_tongue.jpg) |
| `jindo_puppy.jpg` | CC0 | beethoven_e | [Jindo puppy.jpg](https://commons.wikimedia.org/wiki/File:Jindo_puppy.jpg) |
| `shiba_smile.jpg` | Public domain | Roberto Vasarri | [Shiba inu taiki.jpg](https://commons.wikimedia.org/wiki/File:Shiba_inu_taiki.jpg) |
| `yellowstone_sunrise.jpg` | Public domain | Yellowstone National Park | [Sunrise on Yellowstone Lake (Flat Mountain Arm) (32985162453).jpg](https://commons.wikimedia.org/wiki/File:Sunrise_on_Yellowstone_Lake_(Flat_Mountain_Arm)_(32985162453).jpg) |
| `quebec_night.jpg` | CC0 | Wilfredor | [Chateau Frontenac illuminated at night in Quebec City.jpg](https://commons.wikimedia.org/wiki/File:Chateau_Frontenac_illuminated_at_night_in_Quebec_City.jpg) |
| `hokkaido_flowers.jpg` | CC0 | TheOneMattMan | [Field of flowers in Hokkaido, Japan.jpg](https://commons.wikimedia.org/wiki/File:Field_of_flowers_in_Hokkaido,_Japan.jpg) |
| `autumn_forest.jpg` | CC0 | Martin Vorel | [Autumn Forest Wet Bark.jpg](https://commons.wikimedia.org/wiki/File:Autumn_Forest_Wet_Bark.jpg) |
| `red_tulips.jpg` | CC0 | Filio | [Tulip Field Tulips Red Holland Nature Flowers.jpg](https://commons.wikimedia.org/wiki/File:Tulip_Field_Tulips_Red_Holland_Nature_Flowers.jpg) |
| `moraine_lake.jpg` | Public domain | Gorgo | [Moraine Lake 17092005.jpg](https://commons.wikimedia.org/wiki/File:Moraine_Lake_17092005.jpg) |

`autumn_forest.jpg`, `red_tulips.jpg`, `moraine_lake.jpg` 는 시연 업로드(웹 API POST, 앱에서 새 글 올리기)용 사진이다.

서버에 올리기 (PhotoBlogServer 에서, 관리자 계정 필요):
`python manage.py shell -c "exec(open('sample_photos/load_samples.py', encoding='utf-8').read())"`
— posts.json 의 제목·내용·게시일(date)대로 관리자 글을 만든다.
