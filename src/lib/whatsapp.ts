const WHATSAPP_NUMBER = '971554511204';

export function waLink(message: string) {
  const encoded = encodeURIComponent(message);
  return WHATSAPP_NUMBER
    ? `https://wa.me/${WHATSAPP_NUMBER}?text=${encoded}`
    : `https://wa.me/?text=${encoded}`;
}

export function openWhatsApp(message: string) {
  window.open(waLink(message), '_blank', 'noopener,noreferrer');
}
