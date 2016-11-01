
#include <string.h>
#include <Arduino.h>
#include <SPI.h>
#include "Adafruit_BLE.h"
#include "Adafruit_BluefruitLE_SPI.h"
#include "Adafruit_BluefruitLE_UART.h"
#include "BluefruitConfig.h"
#include <Adafruit_NeoPixel.h>

#define PIXEL_PIN                6
#define NUMPIXELS               32
#define RELAY_PIN               11

Adafruit_NeoPixel pixels = Adafruit_NeoPixel(NUMPIXELS, PIXEL_PIN, NEO_GRB + NEO_KHZ800);
Adafruit_BluefruitLE_SPI ble(BLUEFRUIT_SPI_CS, BLUEFRUIT_SPI_IRQ, BLUEFRUIT_SPI_RST);

uint8_t readPacket(Adafruit_BLE *ble, uint16_t timeout);
void printHex(const uint8_t * data, const uint32_t numBytes);
extern uint8_t packetbuffer[];

uint8_t red = 0;
uint8_t green = 0;
uint8_t blue = 0;
uint8_t flick_lo = 0;
uint8_t flick_hi = 0;


void setColors(uint8_t red, uint8_t green, uint8_t blue, uint8_t flick_lo, uint8_t flick_hi) {
    for(int i=0; i<pixels.numPixels(); i++) {
      int flicker = random(flick_lo, flick_hi);
      int r1 = red-flicker;
      int g1 = green-flicker;
      int b1 = blue-flicker;
      if(g1<0) g1=0;
      if(r1<0) r1=0;
      if(b1<0) b1=0;
      pixels.setPixelColor(i,r1,g1, b1);
    }
    pixels.show();
}

void setup(void)
{
  pixels.setBrightness(12);
  pixels.begin();
  setColors(red, green, blue, flick_lo, flick_hi);
  pinMode(RELAY_PIN, OUTPUT);
  
  Serial.begin(115200);
  Serial.println(F("Serial begin"));
  if ( !ble.begin(VERBOSE_MODE) )
  {
    Serial.println(F("Couldn't find Bluefruit"));
  }
  ble.echo(false);
  ble.info();
  ble.verbose(false);
  
  while (! ble.isConnected()) {
    delay(500);
  }
  Serial.println( F("Connect - switching to DATA mode!") );
  ble.setMode(BLUEFRUIT_MODE_DATA);
}

void loop(void)
{

  
  uint8_t len = readPacket(&ble, BLE_READPACKET_TIMEOUT);
  if ((len == 0) && (red == 0 && green == 0 && blue == 0))
    return;
  else if (red > 0 || green > 0 || blue > 0) {
    setColors(red, green, blue, flick_lo, flick_hi);
  }

  printHex(packetbuffer, len);
   
  // Color mode
  if (packetbuffer[1] == 'C') {
    Serial.println( F("Color mode!") );
    red = packetbuffer[2];
    green = packetbuffer[3];
    blue = packetbuffer[4];
    flick_lo = packetbuffer[5];
    flick_hi = packetbuffer[6];
    setColors(red, green, blue, flick_lo, flick_hi);
  }
  if (packetbuffer[1] == 'B') {
    uint8_t value = packetbuffer[2];
    if (value == 0x30) { 
      digitalWrite(RELAY_PIN, LOW);
    }
    if (value == 0x31) { 
      digitalWrite(RELAY_PIN, HIGH);    
    }
  }

}

