package com.sarada.trading.marketdata.application;

import java.util.List;

/**
 * Nifty 200 index constituents (NSE tradingsymbols).
 * Update quarterly when NSE reconstitutes the index.
 */
public final class Nifty200Stocks {

    private Nifty200Stocks() {}

    public static final List<String> SYMBOLS = List.of(
            // ── Nifty 50 ──
            "ADANIENT", "ADANIPORTS", "APOLLOHOSP", "ASIANPAINT", "AXISBANK",
            "BAJAJ-AUTO", "BAJFINANCE", "BAJAJFINSV", "BPCL", "BHARTIARTL",
            "BRITANNIA", "CIPLA", "COALINDIA", "DIVISLAB", "DRREDDY",
            "EICHERMOT", "GRASIM", "HCLTECH", "HDFCBANK", "HDFCLIFE",
            "HEROMOTOCO", "HINDALCO", "HINDUNILVR", "ICICIBANK", "ITC",
            "INDUSINDBK", "INFY", "JSWSTEEL", "KOTAKBANK", "LT",
            "M&M", "MARUTI", "NESTLEIND", "NTPC", "ONGC",
            "POWERGRID", "RELIANCE", "SBILIFE", "SBIN", "SHRIRAMFIN",
            "SUNPHARMA", "TCS", "TATACONSUM", "TATAMOTORS", "TATASTEEL",
            "TECHM", "TITAN", "TRENT", "ULTRACEMCO", "WIPRO",

            // ── Nifty Next 50 ──
            "ABB", "ADANIGREEN", "ADANIPOWER", "AMBUJACEM", "ATGL",
            "BANKBARODA", "BEL", "BOSCHLTD", "CANBK", "CHOLAFIN",
            "COLPAL", "DLF", "DABUR", "GAIL", "GODREJCP",
            "HAL", "HAVELLS", "ICICIGI", "ICICIPRULI", "INDHOTEL",
            "IOC", "IRCTC", "IRFC", "JIOFIN", "JINDALSTEL",
            "LTIM", "LTTS", "LODHA", "LUPIN", "MANKIND",
            "MARICO", "MAXHEALTH", "MOTHERSON", "NHPC", "NAUKRI",
            "PAGEIND", "PEL", "PFC", "PIDILITIND", "PNB",
            "RECLTD", "SIEMENS", "SRF", "TATAPOWER", "TORNTPHARM",
            "TVSMOTOR", "VBL", "VEDL", "ZOMATO", "ZYDUSLIFE",

            // ── Remaining Nifty 200 ──
            "ACC", "ABCAPITAL", "ABFRL", "ALKEM", "APLAPOLLO",
            "ASTRAL", "ATUL", "AUROPHARMA", "BALKRISIND", "BANDHANBNK",
            "BERGEPAINT", "BIOCON", "CANFINHOME", "CUMMINSIND", "CONCOR",
            "COFORGE", "CROMPTON", "CUB", "DEEPAKNTR", "DEVYANI",
            "DIXON", "ESCORTS", "EXIDEIND", "FEDERALBNK", "FORTIS",
            "GMRINFRA", "GLENMARK", "GODREJPROP", "GRANULES", "GUJGASLTD",
            "HINDPETRO", "HONAUT", "IDFCFIRSTB", "IEX", "INDIANB",
            "INDUSTOWER", "IPCALAB", "JKCEMENT", "JUBLFOOD", "KALYANKJIL",
            "KEI", "KPITTECH", "LALPATHLAB", "LAURUSLABS", "LICHSGFIN",
            "MGL", "MANAPPURAM", "MFSL", "MCX", "METROPOLIS",
            "MPHASIS", "MRF", "MUTHOOTFIN", "NATIONALUM", "NAVINFLUOR",
            "NMDC", "OBEROIRLTY", "OFSS", "PERSISTENT", "PETRONET",
            "PHOENIXLTD", "PIIND", "POLYCAB", "PRESTIGE", "PVRINOX",
            "RAMCOCEM", "RBLBANK", "SBICARD", "SHREECEM", "SONACOMS",
            "STARHEALTH", "SUNTV", "SUPREMEIND", "SYNGENE", "TATACHEM",
            "TATACOMM", "TATAELXSI", "THERMAX", "TIMKEN", "TORNTPOWER",
            "TRIDENT", "UNIONBANK", "UNITDSPR", "VOLTAS", "WHIRLPOOL",
            "YESBANK", "ZEEL", "SAIL", "OIL", "BHEL",
            "GNFC", "DMART", "HDFCAMC", "IREDA", "JSWENERGY",
            "KAYNES", "PAYTM", "POLICYBZR", "LLOYDSME", "DELHIVERY"
    );
}
