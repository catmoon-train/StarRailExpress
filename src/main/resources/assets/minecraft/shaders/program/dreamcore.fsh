#version 150
uniform sampler2D DiffuseSampler; in vec2 texCoord; out vec4 fragColor; uniform float Strength; uniform float Time;
void main(){ vec2 uv=texCoord + vec2(sin(texCoord.y*40.0+Time*1.2), cos(texCoord.x*30.0-Time))*0.002*Strength; vec4 c=texture(DiffuseSampler,uv); c.r += 0.05*Strength; c.b += 0.08*Strength; fragColor=vec4(c.rgb,c.a);} 
