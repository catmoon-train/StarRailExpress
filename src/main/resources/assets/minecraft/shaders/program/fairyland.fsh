#version 150
uniform sampler2D DiffuseSampler; in vec2 texCoord; out vec4 fragColor; uniform float Strength; uniform float Time;
void main(){ vec4 c=texture(DiffuseSampler,texCoord); float glow=0.5+0.5*sin(Time*1.6+texCoord.y*18.0); c.rgb += vec3(0.18,0.28,0.32)*glow*Strength; fragColor=vec4(c.rgb, c.a);} 
