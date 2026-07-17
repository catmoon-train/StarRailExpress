from PIL import Image
o = Image.open(r"C:\Users\hai'man\Downloads\Bow_JE2_BE1.png").convert('RGBA').resize((16,16), Image.NEAREST)
g = Image.open(r"D:\gengxin\StarRailExpress\src\main\resources\assets\noellesroles\textures\item\sixty_seconds_hunting_bow.png")
match = sum(1 for y in range(16) for x in range(16) if (o.getpixel((x,y))[3]>50)==(g.getpixel((x,y))[3]>50))
print(f'Shape match: {match}/256 pixels')
