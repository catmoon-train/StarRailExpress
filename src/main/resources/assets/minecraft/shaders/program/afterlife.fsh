#version 150
uniform sampler2D DiffuseSampler; in vec2 texCoord; out vec4 fragColor; uniform float Strength; uniform float Time;
void main(){ vec4 c=texture(DiffuseSampler,texCoord); float g=dot(c.rgb,vec3(0.299,0.587,0.114)); vec3 cold=mix(c.rgb, vec3(g)*vec3(0.9,0.95,1.05), Strength); float vign=1.0-smoothstep(0.2,0.9,distance(texCoord,vec2(0.5))); fragColor=vec4(cold*(0.8+0.2*vign),c.a);} 
