import { motion } from 'framer-motion';

const BASE = 'https://cdn.myportfolio.com/e4dea4af-9f97-454b-9e73-4b40a2da4143';

const strips = [
  `${BASE}/28189d9f-fe44-46ef-913d-ef6ea7d80ec9_rw_1920.png?h=ab38ecb0b539d936fe9343adc48bfbc3`,
  `${BASE}/55f2fcf7-0e81-4085-acf5-4498675a245a.png?h=7b4dcaddebcf5333879215496b290b7b`,
  `${BASE}/a62cff8e-12d6-4f64-ac9c-83de50e0c0d8_rw_1920.png?h=67249c757c0b64fe0f8f999c6da06bb6`,
  `${BASE}/76481676-ee8f-4890-b301-d9360ef539d8_rw_1920.JPG?h=815815c6d5052485dd0f8ea19d70efc8`,
  `${BASE}/741d9b5d-9db1-4428-a94b-e327866aaf9b_rw_1920.png?h=0ff7a2e0ee7805d673cf653e2149c872`,
];

export default function VisualStrip() {
  return (
    <div className="relative overflow-hidden bg-[#060606]">
      <motion.div
        initial={{ opacity: 0 }}
        whileInView={{ opacity: 1 }}
        viewport={{ once: true }}
        transition={{ duration: 1 }}
        className="flex gap-2 py-3"
        style={{ width: 'max-content', animation: 'stripScroll 30s linear infinite' }}
      >
        {[...strips, ...strips].map((src, i) => (
          <div key={i} className="relative overflow-hidden flex-shrink-0 w-[260px] md:w-[320px] h-[180px] md:h-[220px]">
            <img
              src={src}
              alt=""
              loading="lazy"
              className="w-full h-full object-cover opacity-60 hover:opacity-90 transition-opacity duration-500"
            />
          </div>
        ))}
      </motion.div>

      <div className="absolute inset-y-0 left-0 w-24 bg-gradient-to-r from-[#060606] to-transparent pointer-events-none z-10" />
      <div className="absolute inset-y-0 right-0 w-24 bg-gradient-to-l from-[#060606] to-transparent pointer-events-none z-10" />

      <style>{`
        @keyframes stripScroll {
          from { transform: translateX(0); }
          to   { transform: translateX(-50%); }
        }
      `}</style>
    </div>
  );
}